# Security policy

## Supported state

This repository is under active development. Security fixes target the current `main` branch. Prototype, demo, draft, and historical artifacts are not separate supported release lines.

## Report a vulnerability

Do not open a public issue or pull request for a suspected vulnerability, exposed secret, authentication bypass, privacy leak, or other security-sensitive defect.

Report it privately to the Zero State maintainers through the existing private project channel. Include:

- the affected repository, path, commit, or endpoint;
- the impact and conditions required to reproduce it;
- minimal reproduction steps;
- whether credentials, personal data, raw scan images, or production systems may be affected.

Do not include live secrets or unnecessary personal data in the report.

## Handling

Maintainers should acknowledge the report privately, preserve evidence, assess affected versions and deployments, rotate exposed credentials when applicable, and coordinate remediation before public disclosure.

For zz code recognition defects, a wrong-but-valid decode is security relevant when it can cause the client to resolve or act on a different valid code. Preserve the image/evidence only under the applicable consent and privacy rules; do not add private qualification images to the repository.

## Repository rules

- Never commit credentials, signing keys, access tokens, private qualification images, or production environment files.
- Keep raw scan photos on device in v1 unless a later accepted specification explicitly changes that rule.
- Do not weaken validation, tests, CI, grammar, check-word verification, or fail-closed behavior to make a build pass.
- Dependency or model additions require license, provenance, and security review before shipping.
