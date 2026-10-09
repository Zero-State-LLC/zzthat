# Camera recognition adapter contract

Status: proposed app contract. Aligns with zzThis spec 004 FR-018 to FR-021 and ZZ-OCR-QUAL-001 in [draft PR #89](https://github.com/Zero-State-LLC/zzthis/pull/89); implementation waits for this spec and the upstream contract to be accepted and pinned.

This contract keeps the camera pipeline engine-neutral. The visual `zz` marks are the code's index markers; there is no separate control glyph or fiducial. They remain literal grammar markers, but are not payload text. Recognition runs on device. Images, crops, and OCR transcripts stay local and are deleted when the capture flow ends.

## Runtime sequence

1. Normalize the captured still image to upright orientation once. All boxes and polygons use normalized coordinates in `[0,1]` over that upright source image, with origin at the top-left.
2. Detect terminal `zz` marker observations and generate plausible opening/closing pair hypotheses from geometry and reading order only. Preserve complete pairs, partial codes, and bare marks as separate, visible candidates. Do not infer a missing endpoint. Do not consult recognized payload text, grammar validity, wordlist proximity, or resolver state to rank/select pairs.
3. Show candidate boxes before payload OCR. If more than one selectable visual candidate exists (including a complete pair alongside a partial/bare candidate), the person selects which candidate to process. Proceed without a tap only when one unambiguous complete pair is the only selectable visual candidate. Selecting a partial/bare candidate does not invoke payload OCR. Overflow, missing endpoints, or unresolved geometry must not be silently truncated or guessed; use the specified retry/clarify/abstain outcome.
4. For the selected complete pair, derive a validated interior ROI that excludes the opening and closing marker patches. Crop and rectify when geometry permits within the qualified envelope. The v1 contract does not impose same-baseline-only pairing and does not globally ban perspective correction: wrapped/multiline support remains in scope, while unsafe, unsupported, or unrectifiable geometry fails closed. Never run payload OCR outside the validated interior ROI.
5. Invoke the platform reader only on that ROI. Return raw ordered text hypotheses, confidence only when the engine supplies meaningful confidence, and line/token geometry when available. Keep the pair ID and geometry provenance attached. Missing confidence remains missing.
6. Reconstruct literal opening and closing `zz` markers around payload text and pass that representation to the shared scanner, grammar, wordlist classifier, and check-word verifier. These shared deterministic components, not the reader, own canonicalization and acceptance. Marker detection confidence is not payload-word confidence.
7. If the selected ROI yields multiple text hypotheses, preserve the engine's order and require Clarify. Never reorder or select a hypothesis using confidence, grammar validity, wordlist distance, or live-code existence.

## Identity and geometry

- A capture result has a stable image-scoped identifier. Marker observations, pair hypotheses, ROI, and recognition hypotheses retain their parent IDs.
- Every complete pair hypothesis has a non-null `pair_id`; an ROI and its recognition output link to exactly one pair. Partial/bare candidates have no payload ROI and cannot invoke payload OCR.
- If rectification is used, the adapter reports the source ROI and a 3×3 homography. Proposed convention for upstream agreement: row-major matrix, homogeneous column vectors, mapping normalized upright source-image coordinates to normalized rectified-ROI coordinates. Recognition geometry is mapped back with the inverse transform before drawing on the source image. No consumer may infer a transform direction from field naming alone.
- Pair and candidate bounds are finite and configured from qualification evidence. Exceeding a bound is an explicit fail-closed result, never truncation that could hide a competing candidate.
- Persist no raw image, crop, transcript, or candidate code in telemetry, logs, API requests, or long-lived storage. Only a person-selected canonical code may enter ordinary resolver lookup.

## Product behavior versus qualification behavior

Product behavior is selection-first for ambiguity: identify and show every selectable visual candidate, then OCR only the selected complete pair. A sole unambiguous complete pair can be read without a tap only when it is the only selectable candidate. The qualification harness has a different measurement obligation: it runs the same reader independently for every complete predicted pair/ROI, associates each observation by `pair_id`, and scores against ground truth only after results are produced. Ground truth must never drive pairing, selection, or recognizer ordering. Thus, per-pair qualification does not authorize runtime OCR of every ambiguous candidate.

The harness preserves engine hypothesis order. Runtime disposition is Clarify when a selected ROI returns more than one hypothesis; deterministic qualification metrics use the protocol-defined top-1 only. Missing hypotheses and pair linkage remain explicit outcomes, not dropped rows.

## Scope decisions and open gates

- Preserve upstream v1 support for two-line/wrapped codes and rectification when qualified. The earlier same-baseline-only / no-perspective proposal conflicts with zzThis FR-009 and the rectification qualification gate; it is not adopted here as a silent scope reduction. Changing that scope requires an explicit revision to the zzThis product spec and qualification matrix before this app contract changes.
- Numeric detector limits, polygon inset, accepted skew/perspective envelope, reason-code mapping, and platform-specific confidence mapping remain open until measured against the governed corpus and frozen in qualification configuration. No values are invented here.
- The upstream proposal is in draft PR #89 and is not yet the merged pin. Before application code lands, reconcile the pair-linkage, coordinate, and homography semantics with the accepted upstream adapter/schema contract, then update the pin and this document together.
