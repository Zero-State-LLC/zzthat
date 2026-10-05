package com.zer0state.zzthat.scan

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zer0state.zzthat.R
import com.zer0state.zzthat.app.AppModel
import com.zer0state.zzthat.grammar.Band
import com.zer0state.zzthat.grammar.CheckResult
import com.zer0state.zzthat.grammar.CheckWord
import com.zer0state.zzthat.grammar.Classifier
import com.zer0state.zzthat.grammar.CodeClass
import com.zer0state.zzthat.grammar.CodeKind
import com.zer0state.zzthat.grammar.Grammar
import com.zer0state.zzthat.grammar.ParseFailure
import com.zer0state.zzthat.grammar.ReadCandidate
import com.zer0state.zzthat.grammar.ReadPipeline
import com.zer0state.zzthat.grammar.ReadSource
import com.zer0state.zzthat.grammar.RecognizedLine
import com.zer0state.zzthat.grammar.ScanKind
import com.zer0state.zzthat.grammar.TypedRead
import com.zer0state.zzthat.grammar.nearWords

/** The on-device reader (runtime.md, Read pipeline step 2): lines with their own confidence. */
fun interface TextReader {
    suspend fun read(bitmap: Bitmap): List<RecognizedLine>
}

/**
 * What tapping a candidate does (zzThis spec 004, Client read pipeline step
 * 6, and the bands). The app never opens a candidate the person did not
 * pick, except one candidate in Accept.
 */
sealed interface CandidateAction {
    data class Resolve(val canonical: String) : CandidateAction

    data class Clarify(val input: ClarifyInput) : CandidateAction

    data class Message(@StringRes val key: Int) : CandidateAction

    companion object {
        fun of(candidate: ReadCandidate, source: ReadSource): CandidateAction = when (candidate.band) {
            Band.ACCEPT -> candidate.canonical?.let(::Resolve) ?: Message(R.string.type_malformed)
            Band.CLARIFY -> (candidate.kind as? ScanKind.Code)?.let { Clarify(ClarifyInput(it.code.words, it.code.kind, source)) }
                ?: Message(R.string.type_malformed)
            // A partial code, or a word below 0.50 (spec 004 Prototype band values).
            Band.RETRY -> if (candidate.kind is ScanKind.Partial) Message(R.string.scan_partial) else Message(R.string.scan_retry)
            Band.ABSTAIN -> when {
                candidate.kind is ScanKind.Bare -> Message(R.string.scan_bare)
                candidate.kind is ScanKind.Code && candidate.check == CheckResult.WrongLength -> Message(R.string.scan_wrong_length)
                else -> Message(R.string.type_malformed)
            }
        }
    }
}

/** The code a Clarify step starts from. */
data class ClarifyInput(val parts: List<String>, val kind: CodeKind, val source: ReadSource)

/**
 * One scan: the photo, the on-device read, and the candidates (runtime.md,
 * Read pipeline). The photo stays on the device, in the caches folder only
 * while the scan runs (FR-004).
 */
class ScanModel {
    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var candidates by mutableStateOf<List<ReadCandidate>>(emptyList())
        private set
    var reading by mutableStateOf(false)
        private set

    /** The one sentence under the photo: an empty read, a band reason, the camera being off, a 429, or the offline wait. */
    @get:StringRes
    var message by mutableStateOf<Int?>(null)

    /** Reads one still photo. Returns the canonical code to open at once when there is one candidate and it is in Accept. */
    suspend fun read(bitmap: Bitmap, app: AppModel, reader: TextReader): String? {
        photo = bitmap
        reading = true
        message = null
        candidates = emptyList()
        val lines = try {
            reader.read(bitmap)
        } catch (error: Exception) {
            emptyList()
        }
        val result = app.pipeline.read(lines)
        reading = false
        candidates = result.candidates
        if (result.candidates.isEmpty()) message = R.string.scan_no_code
        return result.autoOpen?.canonical
    }

    fun cameraIsOff() {
        message = R.string.scan_camera_off
    }

    /** The scan ends: the photo is deleted (FR-004, T063). */
    fun end(app: AppModel) {
        app.photos.end()
        photo = null
        candidates = emptyList()
        message = null
    }
}

/** Typed entry (US2a): the same grammar and the same resolve call. The whole input must be one code or one bare mark (Section 2.2a G8). */
class TypeModel {
    var text by mutableStateOf("")

    @get:StringRes
    var message by mutableStateOf<Int?>(null)

    fun action(pipeline: ReadPipeline): CandidateAction = when (val read = pipeline.readTyped(text)) {
        is TypedRead.Refused -> if (read.reason == ParseFailure.EMPTY) {
            CandidateAction.Message(R.string.type_empty)
        } else {
            CandidateAction.Message(R.string.type_malformed)
        }
        is TypedRead.Read -> CandidateAction.of(read.candidate, ReadSource.TYPED)
    }
}

/**
 * Clarify (design/UX.md, Scan with the camera, step 4): one uncertain word
 * at a time with its list candidates and "as written", a check-word mismatch
 * with the words as read, or a field code, handle, or name that a person
 * confirms before it opens (spec 004 FR-010).
 */
sealed interface ClarifyStep {
    /** [position] is 1-based, as the classifier reports it. */
    data class NearWord(val position: Int, val written: String, val candidates: List<String>) : ClarifyStep

    data object CheckMismatch : ClarifyStep

    data object WrongLength : ClarifyStep

    data object Confirm : ClarifyStep

    data class Done(val canonical: String) : ClarifyStep
}

object ClarifyLogic {
    /**
     * The next step for these parts. Parts the person kept as written are
     * never offered again, and a picked candidate is classified again. A
     * camera reading opens only after the person has looked at it once.
     */
    fun next(parts: List<String>, keptAsWritten: Set<Int>, confirmed: Boolean, pipeline: ReadPipeline): ClarifyStep {
        val code = Grammar.parse(Grammar.format(parts)).codeOrNull() ?: return ClarifyStep.CheckMismatch
        val codeClass = Classifier.classify(code, pipeline.classifyList?.words.orEmpty())
        val position = codeClass.nearWords.keys.sorted().firstOrNull { it !in keptAsWritten }
        if (position != null) {
            return ClarifyStep.NearWord(position, parts[position - 1], codeClass.nearWords.getValue(position))
        }
        val list = pipeline.checkList
        if (codeClass == CodeClass.Word && list != null && code.words.all { it in list }) {
            when (CheckWord.verify(code, list)) {
                CheckResult.CheckMismatch -> return ClarifyStep.CheckMismatch
                CheckResult.WrongLength -> return ClarifyStep.WrongLength
                else -> Unit
            }
        }
        if (!confirmed) return ClarifyStep.Confirm
        return ClarifyStep.Done(code.canonical)
    }
}

class ClarifyModel(val input: ClarifyInput) {
    var parts by mutableStateOf(input.parts)
        private set
    var keptAsWritten by mutableStateOf(emptySet<Int>())
        private set

    // Typed field codes, handles, and names open as typed (spec 004 bands);
    // only the camera asks a person to confirm them.
    var confirmed by mutableStateOf(input.source == ReadSource.TYPED)
        private set

    fun step(pipeline: ReadPipeline): ClarifyStep = ClarifyLogic.next(parts, keptAsWritten, confirmed, pipeline)

    val canonical: String get() = Grammar.format(parts)

    /** Picking a candidate snaps that part (Section 2.2a G1). The person has now looked at the reading. */
    fun pick(word: String, position: Int) {
        parts = parts.toMutableList().also { it[position - 1] = word }
        confirmed = true
    }

    fun keepAsWritten(position: Int) {
        keptAsWritten = keptAsWritten + position
        confirmed = true
    }

    /** The person read the code back and keeps it as written. */
    fun confirm() {
        confirmed = true
    }
}
