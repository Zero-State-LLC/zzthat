# Repository hygiene audit — 2026-10-06

Scope: documentation/source-of-truth hygiene, collaboration metadata, native-app/spec parity, recognition/privacy consistency, and visible repository controls. This audit does not claim access to every GitHub organization or repository setting.

## Observed

- README, CONTRIBUTING, CODEOWNERS, pull-request template, spec lint, native app CI, end-to-end workflow, native Swift/Kotlin source, shared grammar code, tests, and pinned zzThis contracts are present.
- `SECURITY.md` was missing and is added by this audit.
- No TODO/FIXME/HACK/XXX markers were returned by repository code search.
- README, spec.md, plan.md, and runtime.md incorrectly described the repo as specification-only / not built even though application source and CI exist. This audit corrects those status statements without claiming acceptance evidence that has not been verified.
- Existing native readers are coupled directly to Vision/ML Kit assumptions. PR #44 now treats those implementations as migration targets behind `RecognitionResult`.
- The committed zzThis pin remains `424395f665587de8a73dae2cbdff3dbd9593b6c3`. It is intentionally not advanced while upstream zzThis PR #89 is draft.
- Historical decisions still mention the disabled cloud-photo path. v1 remains no-cloud and raw scan photos stay on device.
- `.github/dependabot.yml` is not present. This audit does not enable dependency automation or repository security settings without an explicit settings decision.

## Hygiene rules applied

1. File existence is not task completion. Unchecked tasks stay unchecked until their named tests/evidence pass.
2. Product authority remains the pinned zzThis contract plus spec 001; native recognizers emit evidence only.
3. Status text distinguishes source present, verification in progress, production readiness, and store readiness.
4. Pin movement is atomic and happens only after the upstream source is accepted.
5. Private OCR qualification images do not enter the repository or CI artifacts.
6. Dependency/model additions require license, provenance, and security review before shipping.

## Follow-up

- Merge upstream zzThis #89 first.
- Advance `ZZTHIS-API-PIN` through the normal sync flow and reconcile #44 against that merged SHA.
- Run the existing spec/app CI and record actual evidence before checking off tasks.
- Decide separately whether to enable Dependabot, secret scanning/push protection, code scanning, private vulnerability reporting, and repository rules. Their state is not established by files in the tree.
