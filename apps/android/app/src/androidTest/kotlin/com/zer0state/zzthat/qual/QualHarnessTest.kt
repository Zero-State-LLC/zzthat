package com.zer0state.zzthat.qual

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zer0state.zzthat.grammar.CheckResult
import com.zer0state.zzthat.grammar.PairRead
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.RecognitionResult
import com.zer0state.zzthat.grammar.VisualCandidate
import com.zer0state.zzthat.grammar.WordListLoader
import com.zer0state.zzthat.scan.CameraPipeline
import com.zer0state.zzthat.scan.MlKitRecognizer
import com.zer0state.zzthat.scan.PhotoDecoder
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * The ZZ-OCR-QUAL-001 harness for the Android reader (tasks.md T027, T023c):
 * the shipped ML Kit pipeline over a folder of photos on a device or
 * emulator, offline. Each complete pair is read on its own, with no
 * selection by truth. Run by `scripts/qual-android.sh`, which copies the
 * photos into the app's private files folder and reads the output back. Without the
 * `zzqual` argument it does nothing, so CI and other runs skip it.
 */
@RunWith(AndroidJUnit4::class)
class QualHarnessTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun harness(): Unit = runBlocking {
        val mode = arguments.getString("zzqual")
        assumeTrue("run by scripts/qual-android.sh", mode != null)
        val dir = File(context.filesDir, "zzqual")
        val version = arguments.getString("zzqual.list") ?: "fixture-7"
        val list = context.assets.open("wordlists/$version.txt").bufferedReader().use { WordListLoader.parse(version, it.readText()) }
        val pipeline = ReadPipeline(list, list)
        when (mode) {
            "quick" -> quick(dir, pipeline)
            "run" -> run(dir, pipeline)
            else -> error("zzqual is quick or run, not $mode")
        }
    }

    private class Sample(val frame: RecognitionResult, val reads: List<PairRead>, val elapsedMs: Double)

    private suspend fun read(file: File, pipeline: ReadPipeline): Sample {
        val camera = CameraPipeline(MlKitRecognizer())
        val image = requireNotNull(PhotoDecoder.decode(file)) { "cannot decode ${file.name}" }
        val start = System.nanoTime()
        var result = camera.detect(image, pipeline)
        val frame = result
        val reads = mutableListOf<PairRead>()
        for (box in frame.visualCandidates.filterIsInstance<VisualCandidate.Complete>()) {
            val (withText, read) = camera.readPair(image, result, box.pair.pairId, pipeline)
            result = withText
            reads += read
        }
        val elapsed = (System.nanoTime() - start) / 1e6
        image.release()
        return Sample(result, reads, elapsed)
    }

    private suspend fun quick(dir: File, pipeline: ReadPipeline) {
        val rows = mutableListOf("file,expected,read,band,result,elapsed_ms")
        var exact = 0
        var falseValid = 0
        val bands = sortedMapOf<String, Int>()
        val lines = File(dir, "truth.csv").readLines().drop(1).filter { it.isNotBlank() }
        for (line in lines) {
            val file = line.substringBefore(',').trim()
            val expected = line.substringAfter(',', "").trim().lowercase().split(' ').filter { it.isNotEmpty() }.sorted()
            val sample = read(File(dir, "images/$file"), pipeline)
            val firsts = sample.reads.mapNotNull { it.candidates.firstOrNull() }
            val codes = firsts.mapNotNull { it.canonical }
            val band = firsts.firstOrNull()?.band?.wire ?: sample.frame.reasonCodes.firstOrNull() ?: "no-code"
            bands[band] = (bands[band] ?: 0) + 1
            val result = when {
                codes.sorted() == expected -> "exact".also { exact++ }
                firsts.any { it.check == CheckResult.Ok && it.canonical !in expected } -> "FALSE-VALID".also { falseValid++ }
                else -> "miss"
            }
            rows += "$file,${expected.joinToString(" ")},${codes.joinToString(" ")},$band,$result,${sample.elapsedMs.toInt()}"
        }
        File(dir, "rows.csv").writeText(rows.joinToString("\n") + "\n")
        val pct = if (lines.isEmpty()) 0.0 else exact * 100.0 / lines.size
        File(dir, "summary.txt").writeText(
            "photos ${lines.size}  exact $exact (${"%.1f".format(pct)}%)  false-valid $falseValid\n" +
                "bands " + bands.entries.joinToString(", ") { "${it.key} ${it.value}" } + "\n",
        )
    }

    /** One `sampleResult` per manifest sample, in manifest order; the host script adds the top-level fields. */
    private suspend fun run(dir: File, pipeline: ReadPipeline) {
        val manifest = JSONObject(File(dir, "manifest.json").readText())
        val entryId = requireNotNull(arguments.getString("zzqual.entry")) { "zzqual.entry is required" }
        val osVersion = requireNotNull(arguments.getString("zzqual.os")) { "zzqual.os is required" }
        val deviceClass = requireNotNull(arguments.getString("zzqual.device")) { "zzqual.device is required" }
        val byHash = File(dir, "images").listFiles().orEmpty().associateBy { sha256(it.readBytes()) }
        val samples = manifest.getJSONArray("samples")
        val results = JSONArray()
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            val hash = sample.getString("asset_sha256")
            val file = requireNotNull(byHash[hash]) { "no image has the hash of ${sample.getString("sample_id")}" }
            val read = read(file, pipeline)
            // Android's org.json keeps null values as JSONObject.NULL.
            val row = JSONObject(read.frame.schemaFields())
            if (read.frame.runtimeOutcome.wire != "completed") {
                // The scorer requires a crashed or failed row to carry no evidence.
                for (key in listOf("fiducials", "rois", "candidates")) row.put(key, JSONArray())
            }
            row.put("sample_id", sample.getString("sample_id"))
            row.put("split", sample.getString("split"))
            row.put("input_image_sha256", hash)
            row.put("device_matrix_entry_id", entryId)
            row.put("os_version", osVersion)
            row.put("device_class", deviceClass)
            row.put("elapsed_ms", Math.round(read.elapsedMs * 1000) / 1000.0)
            row.put("runtime_memory_bytes", JSONObject.NULL)
            results.put(row)
        }
        File(dir, "results.json").writeText(results.toString(1))
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
