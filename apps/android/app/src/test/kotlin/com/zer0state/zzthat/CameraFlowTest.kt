package com.zer0state.zzthat

import com.zer0state.zzthat.CaptureFixtures.line
import com.zer0state.zzthat.create.WriteCheckModel
import com.zer0state.zzthat.grammar.Band
import com.zer0state.zzthat.grammar.CaptureConfig
import com.zer0state.zzthat.grammar.CodeKind
import com.zer0state.zzthat.grammar.PixelRect
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.ReadSource
import com.zer0state.zzthat.grammar.ReasonCodes
import com.zer0state.zzthat.grammar.VisualCandidate
import com.zer0state.zzthat.grammar.WordList
import com.zer0state.zzthat.scan.CameraPipeline
import com.zer0state.zzthat.scan.CameraSession
import com.zer0state.zzthat.scan.CandidateAction
import com.zer0state.zzthat.scan.ClarifyInput
import com.zer0state.zzthat.scan.ScanModel
import com.zer0state.zzthat.scan.ScanRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The Scan and Write check flows on the RecognitionResult boundary
 * (runtime.md, Read pipeline; recognition-adapter.md; tasks.md T023 and
 * T102): the reader's marker pass, selection before payload OCR, the reader
 * on the selected ROI only, and confirm-only by default.
 */
class CameraFlowTest {
    private val fixture7 = WordList("fixture-7", listOf("copper", "lantern", "sky", "maple", "river", "harbor", "falcon"))
    private val full = PixelRect(0, 0, CaptureFixtures.IMAGE.width, CaptureFixtures.IMAGE.height)

    /** An app whose server reports the bundled fixture-7 list, so the local check word runs. */
    private fun app() = TestApp.make(TestApp.demoService(), bundledLists = listOf(fixture7)).also { runBlocking { it.refreshDiscovery() } }

    private fun session(page: List<com.zer0state.zzthat.grammar.RecognizedLine>) = FixtureRecognizer(page).let { it to CameraPipeline(it) }

    @Test
    fun theDemoServerReportsFixture7() {
        assertEquals("fixture-7", app().pipeline.checkList?.version)
    }

    /** One complete pair is the only candidate: it is read at once, from its ROI only, and confirm-only waits for the person. */
    @Test
    fun aSoleCompletePairIsReadFromItsRoiAndWaitsForConfirm(): Unit = runBlocking {
        val app = app()
        val (reader, camera) = session(listOf(line("zz-copper-lantern-sky-zz")))
        val scan = ScanModel()
        val image = FakeImage()
        assertNull("confirm-only: nothing opens without a confirm", scan.read(image, app, camera))
        assertEquals("the marker pass, then the ROI only", listOf(full, PixelRect(320, 64, 1040, 196)), reader.regions)
        assertEquals("the crop is released once read", listOf(PixelRect(320, 64, 1040, 196)), image.releasedCrops)
        val row = scan.rows.single() as ScanRow.Read
        assertEquals("zz-copper-lantern-sky-zz", row.candidate.canonical)
        assertEquals(Band.CLARIFY, row.candidate.band)
        assertTrue(row.candidate.confirmOnly)
        assertNull(scan.message)
        // The tap goes to Clarify with the full code; Clarify's camera path asks for one confirm.
        assertEquals(
            CandidateAction.Clarify(ClarifyInput(listOf("copper", "lantern", "sky"), CodeKind.PLAIN, ReadSource.CAMERA)),
            scan.select(row, app, camera),
        )
        assertEquals("a tap on a read row reads nothing again", 2, reader.regions.size)
    }

    /** With a PASS receipt (confirm-only off) the same photo opens at once, as the spec 004 band table says. */
    @Test
    fun withConfirmOnlyOffTheSolePairInAcceptOpens(): Unit = runBlocking {
        val (_, camera) = session(listOf(line("zz-copper-lantern-sky-zz")))
        val pipeline = ReadPipeline(fixture7, fixture7, CaptureConfig.DEFAULT.copy(confirmOnly = false))
        val outcome = CameraSession().begin(FakeImage(), camera, pipeline) as CameraSession.Outcome.Rows
        assertEquals("zz-copper-lantern-sky-zz", outcome.read?.autoOpen?.canonical)
    }

    /** Two codes: nothing is read until the person picks, and then only the picked pair. */
    @Test
    fun severalCandidatesWaitAndOnlyTheSelectedPairIsRead(): Unit = runBlocking {
        val app = app()
        val (reader, camera) = session(listOf(line("zz-river-falcon-sky-zz"), line("zz-copper-lantern-sky-zz", y = 0.5)))
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app, camera))
        assertEquals("no payload OCR before a pick", listOf(full), reader.regions)
        assertEquals(2, scan.rows.size)
        assertTrue(scan.rows.all { it is ScanRow.Unread && it.candidate is VisualCandidate.Complete })
        val second = scan.rows[1]
        val action = scan.select(second, app, camera)
        assertEquals(2, reader.regions.size)
        val crop = reader.regions[1]
        assertTrue("the second pair's ROI, below the first line", crop.top > 0.2 * CaptureFixtures.IMAGE.height)
        assertTrue(action is CandidateAction.Clarify)
        assertTrue("the first pair stays unread", scan.rows[0] is ScanRow.Unread)
        assertEquals("zz-copper-lantern-sky-zz", (scan.rows[1] as ScanRow.Read).candidate.canonical)
    }

    @Test
    fun partialAndBareCandidatesNeverRunPayloadOcr(): Unit = runBlocking {
        val app = app()
        val (reader, camera) = session(listOf(line("zz-copper-lantern-sky-zz"), line("see zz-river", y = 0.4), line("zz", y = 0.7)))
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app, camera))
        assertEquals(3, scan.rows.size)
        assertEquals(listOf(full), reader.regions)
        val partial = scan.rows.first { (it as? ScanRow.Unread)?.candidate is VisualCandidate.Partial }
        val bare = scan.rows.first { (it as? ScanRow.Unread)?.candidate is VisualCandidate.Bare }
        assertEquals(CandidateAction.Message(R.string.scan_partial), scan.select(partial, app, camera))
        assertEquals(CandidateAction.Message(R.string.scan_bare), scan.select(bare, app, camera))
        assertEquals(listOf(full), reader.regions)
    }

    /** A code wrapped onto a second baseline is two partial candidates (v1 same-baseline pairing). */
    @Test
    fun aCodeAcrossTwoBaselinesIsNotRead(): Unit = runBlocking {
        val app = app()
        val (reader, camera) = session(listOf(line("zz-copper-lantern"), line("sky-zz", y = 0.25)))
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app, camera))
        assertEquals(2, scan.rows.count { (it as ScanRow.Unread).candidate is VisualCandidate.Partial })
        assertEquals(listOf(full), reader.regions)
    }

    @Test
    fun overflowShowsNoCandidateAndReadsNoPayload(): Unit = runBlocking {
        val app = app()
        val markers = (1..9).joinToString(" ") { "zz" }
        val (reader, camera) = session(listOf(line(markers, cell = 0.01)))
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app, camera))
        assertTrue(scan.rows.isEmpty())
        assertEquals(R.string.scan_retry, scan.message)
        assertEquals(listOf(ReasonCodes.MARKER_LIMIT_EXCEEDED), scan.session.result?.reasonCodes)
        assertEquals(listOf(full), reader.regions)
    }

    @Test
    fun aPhotoWithNoMarkerSaysNoCode(): Unit = runBlocking {
        val (_, camera) = session(listOf(line("the buzz word")))
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app(), camera))
        assertEquals(R.string.scan_no_code, scan.message)
    }

    @Test
    fun aReaderFailureAbstainsAndACancelPropagates(): Unit = runBlocking {
        val reader = FixtureRecognizer(listOf(line("zz-copper-lantern-sky-zz")), failWith = IllegalStateException("OCR failed"))
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app(), CameraPipeline(reader)))
        assertEquals(R.string.scan_no_code, scan.message)
        assertEquals(listOf(ReasonCodes.RECOGNIZER_UNAVAILABLE), scan.session.result?.reasonCodes)
        reader.failWith = CancellationException("scan ended")
        try {
            ScanModel().read(FakeImage(), app(), CameraPipeline(reader))
            fail("expected cancellation")
        } catch (expected: CancellationException) {
            assertEquals("scan ended", expected.message)
        }
    }

    @Test
    fun aRoiWithNoTextAsksForAnotherPhoto(): Unit = runBlocking {
        val reader = FixtureRecognizer(listOf(line("zz-copper-lantern-sky-zz")), cropLines = { emptyList() })
        val scan = ScanModel()
        assertNull(scan.read(FakeImage(), app(), CameraPipeline(reader)))
        assertEquals(R.string.scan_retry, scan.message)
        assertTrue("the pair stays a visible candidate", scan.rows.single() is ScanRow.Unread)
    }

    /** Missing line confidence is explicit: the reading goes to Clarify, never Accept, even with confirm-only off. */
    @Test
    fun missingConfidenceClarifies(): Unit = runBlocking {
        val (_, camera) = session(listOf(line("zz-copper-lantern-sky-zz", confidence = null)))
        val pipeline = ReadPipeline(fixture7, fixture7, CaptureConfig.DEFAULT.copy(confirmOnly = false))
        val read = (CameraSession().begin(FakeImage(), camera, pipeline) as CameraSession.Outcome.Rows).read!!
        assertEquals(listOf(Band.CLARIFY), read.candidates.map { it.band })
        assertFalse(read.candidates.single().confirmOnly)
    }

    /** A rescan is a new attempt: a new capture id, and nothing carried from the last photo. */
    @Test
    fun aRescanIsANewAttempt(): Unit = runBlocking {
        val app = app()
        val (reader, camera) = session(listOf(line("zz-copper-lantern-sky-zz")))
        val scan = ScanModel()
        scan.read(FakeImage(), app, camera)
        val first = scan.session.result!!.captureId
        reader.page = listOf(line("the buzz word"))
        scan.read(FakeImage(), app, camera)
        assertNotEquals(first, scan.session.result!!.captureId)
        assertTrue(scan.rows.isEmpty())
        assertTrue(scan.session.result!!.candidates.isEmpty())
    }

    /** FR-004 and FR-005: the scan writes nothing; ending it deletes the photo and drops every piece of evidence. */
    @Test
    fun theScanPersistsNothingAndEndingItDropsTheEvidence(): Unit = runBlocking {
        val app = app()
        val photo = app.photos.begin(byteArrayOf(1, 2, 3))
        val folder = photo.parentFile!!
        val before = folder.walkTopDown().toSet()
        val (_, camera) = session(listOf(line("zz-copper-lantern-sky-zz")))
        val scan = ScanModel()
        scan.read(FakeImage(), app, camera)
        assertEquals("no file was written during the read", before, folder.walkTopDown().toSet())
        scan.end(app)
        assertFalse(photo.exists())
        assertNull(scan.session.result)
        assertNull(scan.session.image)
        assertTrue(scan.rows.isEmpty())
    }

    /** No log, print, file write, or store in the Scan feature's code (FR-004, FR-005; recognition.md, Data lifetime). */
    @Test
    fun theScanSourcesWriteNothing() {
        val folder = File("src/main/kotlin/com/zer0state/zzthat/scan")
        val forbidden = listOf("android.util.Log", "Log.", "println", "printStackTrace", "FileOutputStream", "writeText", "writeBytes", "openFileOutput", "DataStore", "SharedPreferences")
        val files = folder.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue(files.size >= 4)
        for (file in files) {
            val source = file.readText()
            for (word in forbidden) assertFalse("${file.name} uses $word", source.contains(word))
        }
    }

    @Test
    fun writeCheckComparesTheSolePairsReading(): Unit = runBlocking {
        val app = app()
        val (_, camera) = session(listOf(line("zz-copper-lantern-sky-zz")))
        val match = WriteCheckModel("zz-copper-lantern-sky-zz")
        match.read(FakeImage(), app, camera)
        assertEquals(WriteCheckModel.Verdict.MATCH, match.verdict)
        val mismatch = WriteCheckModel("zz-river-falcon-sky-zz")
        mismatch.read(FakeImage(), app, camera)
        assertEquals(WriteCheckModel.Verdict.MISMATCH, mismatch.verdict)
    }

    @Test
    fun writeCheckWithTwoCodesReadsOnlyThePickedOne(): Unit = runBlocking {
        val app = app()
        val (reader, camera) = session(listOf(line("zz-river-falcon-sky-zz"), line("zz-copper-lantern-sky-zz", y = 0.5)))
        val model = WriteCheckModel("zz-copper-lantern-sky-zz")
        model.read(FakeImage(), app, camera)
        assertNull(model.verdict)
        assertEquals(listOf(full), reader.regions)
        model.pick(model.rows[1], app, camera)
        assertEquals(2, reader.regions.size)
        assertEquals(WriteCheckModel.Verdict.MATCH, model.verdict)
        model.end(app)
        assertNull(model.session.result)
    }
}
