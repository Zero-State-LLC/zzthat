# Recognition integration contract

Status: normative for the recognition boundary. T023 to T025 are built on both platforms (Android #85, iOS #86) and not yet qualified.
Upstream authority: zzThis spec 004 and ZZ-OCR-QUAL-001 at the pinned commit (`contracts/ZZTHIS-API-PIN`, which contains merged zzThis #89).

## Boundary

Both native apps expose the same conceptual adapter contract:

```text
RecognitionResult
  engine_id: non-empty string
  engine_version: non-empty string
  fiducials: optional ordered endpoint evidence
    role: opening | closing
    region
    detection_score: optional
    marker_glyph: exact | tolerant, with the raw token (zzThis spec 004 FR-024; PENDING P-1)
  roi / orientation / normalization_transform (crop, rotation angle, scale factor): optional
  candidates: ordered payload list
    raw_text: string
    confidence: optional engine-native meaningful score
    regions / line_provenance: optional
    engine_rank: integer >= 1 (engine order, immutable)
    line_id: optional
  request: max_hypotheses, engine_id, engine_version
  capture_quality: optional
```

Vendor SDK objects stop at the adapter. `ZZCore` and `:core-grammar` consume normalized evidence only. They own scanner rules, grammar/classification, wordlist behavior, check-word verification, and decision bands.

## Platform implementations

| Platform | Candidate | v1 disposition |
|---|---|---|
| iOS | Apple Vision accurate text recognition | Baseline, but release eligibility still requires upstream qualification PASS |
| Android | Bundled ML Kit Latin text recognition | Qualification candidate |
| Android | PP-OCR mobile/ONNX prototype | Qualification candidate; cannot become shipping dependency before license/security/package review |
| Either | cloud OCR or VLM | Not reachable in V1. A future VLM is a v2 tiebreaker among decoder offers only, capped at Clarify; an online VLM is opt-in and off by default (zzThis spec 004 Q18, D-2026-10-19-04) |
| Either | custom zz model | v2 only |

## Fiducial-first camera contract

For camera input, terminal `zz` marks are structural index/fiducial marks as well as literal grammar markers. The app first detects and pairs endpoint regions, uses the pair to localize/orient the code ROI, normalizes that ROI by crop, rotation, and scaling only, then recognizes the interior payload. It reconstructs normalized evidence with literal boundary markers before shared grammar runs. Endpoint confidence is not payload-word confidence.

One/missing/cut endpoint cannot be inferred. Ambiguous pairs remain multiple candidates. Pair selection cannot consult resolver existence, live-code data, or payload wordlist proximity. Typed input bypasses visual fiducial detection and continues to use literal grammar markers.

## Attempt lifecycle

```text
IDLE
 -> CAPTURING
 -> FINDING_FIDUCIALS
 -> LOCALIZING_ROI
 -> RECOGNIZING_PAYLOAD
 -> DECODING
 -> ACCEPT | CLARIFY | RETRY | ABSTAIN
 -> RESOLVING only from ACCEPT or explicit user selection/confirmation required by spec
 -> IDLE/RESULT

RETRY -> CAPTURING or TYPED_ENTRY
CLARIFY -> DECODING after user input, or TYPED_ENTRY/CANCEL
ABSTAIN -> TYPED_ENTRY/RESCAN/CANCEL
```

A new image creates a new attempt id. No confidence, candidate ordering, or image state carries across attempts. Two Retry results in one scan flow surface typed entry alongside rescan. There is no cloud escalation.

## Data lifetime

| Data | Lifetime / destination |
|---|---|
| Raw image | Local cache only; delete/end access when attempt/flow ends |
| Vendor recognition objects | Memory for current attempt only |
| RecognitionResult | Memory for current attempt/clarification only |
| Canonical code | May proceed to API only after shared decoder/user-selection gates |
| Qualification private image | Private corpus only; never Git/CI/PR/logs |
| Debug logs | No image bytes, candidate text, canonical code, token, or private corpus identifiers that can reveal content |

Cancellation, app background/termination recovery, decode exception, recognizer exception, and navigation away all use the same cleanup path for the raw image and transient evidence.

## Error mapping

- recognizer unavailable/init failure -> Abstain with typed entry;
- no usable fiducial pair -> Retry;
- one/cut/occluded endpoint -> Retry;
- multiple plausible endpoint pairings -> list/Clarify; never choose by resolver or payload plausibility;
- unusable ROI/rectification -> Retry;
- no payload text after a valid ROI -> Retry;
- partial marker / recoverable image-quality failure -> Retry;
- unsupported script, reserved symbol, invalid grammar, unsafe evidence -> Abstain;
- near-word, uncertain word, check-word ambiguity -> Clarify;
- multiple valid candidates -> list all and wait for a user selection;
- wrong-but-valid candidate -> must be caught by qualification evidence; resolver existence is never used as a recognition oracle.

Engine exceptions never become Accept and never cause a network fallback.

## Configuration

Engine/model/runtime versions, preprocessing, and confidence-to-band mapping are explicit configuration with test coverage. Release builds use the qualified pinned configuration. Debug/local builds may expose candidate selection for qualification, but Release has one promoted adapter per platform and no runtime remote switch that can bypass qualification.

## Upstream pin movement

Do not copy unmerged upstream prose into the committed zzThis pin. #44 and #45 reconciled this contract against the merged spec 004, and the adapters were built against it (#85, #86). Each pin movement uses this flow:

1. advance `contracts/ZZTHIS-API-PIN` through the existing sync flow;
2. regenerate/sync governed artifacts;
3. reconcile this contract against the merged spec 004 and qualification protocol;
4. run spec lint, pin checks, shared vectors, native tests, and qualification adapter tests in the same PR;
5. record any intentional downstream delta as a ZQ decision rather than silently diverging.

## Release and rollback

Camera Accept is enabled only for an adapter/configuration with a PASS qualification receipt. FAIL or INCOMPLETE means no camera Accept for that platform release. Typed entry remains available.

A regression can roll back to the last qualified adapter/configuration or disable camera Accept. Rollback cannot enable cloud OCR, lower shared decoder validation, or change the wordlist/check-word algorithm.

## Traceability

| Upstream invariant | zzThat implementation | Test/evidence | Task owner |
|---|---|---|---|
| Fiducial-first localization | native endpoint detector/pairer + ROI/rectification | localization/pairing corpus tests | T023 |
| Engine-neutral evidence | native RecognitionResult adapters | adapter contract tests | T023 |
| Same decoder semantics | ZZCore / core-grammar | shared vectors | T020-T022 |
| Raw image local only | cache + cleanup path | lifecycle/network tests | T023 |
| Missing confidence explicit | optional confidence | adapter tests | T022-T023 |
| Multi-code never auto-pick | Scan UI candidate list | UI/instrumented test | T040 |
| Two Retry fallback | Scan state machine | state/UI test | T023/T040 |
| No cloud v1 | no network reader dependency/path | manifest + network test | T023/T075 |
| Qualified engine only | release config | PASS receipt + config test | T023 |
| Rollback safe | last-qualified/disable-Accept path | release-config test | T023 |

## Qualification artifact consumption

At the current pin (zzThat #76), zzThat consumes the upstream ZZ-OCR-QUAL-001 manifest schema, receipt schema, and scoring rules as test/qualification contracts. It does not fork them. Native harness output must validate against the pinned receipt schema, and fixture/replay data must validate against the pinned manifest schema. Any platform-only field belongs in adapter configuration or the receipt's permitted review/configuration area, not in a second incompatible schema.

The public repository may contain synthetic fixture metadata and generated non-sensitive test images. Private corpus images remain outside Git and CI artifacts. A native qualification runner may read them from an operator-supplied local path; that path and its contents are never committed.
