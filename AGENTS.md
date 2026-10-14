# Agent contract: zzThat

You are working in **zzThat** (`Zero-State-LLC/zzthat`): the free iOS and Android apps for handwritten zz codes.
This file is the canonical contract for Cursor, Claude, Codex, Grok, and OpenClaw.
Harness adapters only point here.

## Intent

Specs live in [`specs/`](specs/). The build contract is [`specs/001-zzthat-apps/runtime.md`](specs/001-zzthat-apps/runtime.md).
Read [`specs/001-zzthat-apps/design.md`](specs/001-zzthat-apps/design.md) before any visual work.
Do not skip to app source. Danny accepts spec 001 before application code lands.

## Commands

| Task | Command |
|---|---|
| Spec lint | `python3 scripts/lint-specs.py` |

Do not invent further commands. App build commands are named in runtime.md and are not run from this spec-only tree.

## Invariants

- Thin client. No resolver, no database of record, no second API in this repo.
- Do not invent the code format. Grammar comes from the pinned zzThis spec.
- Do not invent design tokens, logos, or icons. Consume zzthis `design/` as design.md says.
- User-facing copy says demo, says check word, and uses no em dashes.
- Human yes (Danny) on store submission, spend, production credentials, and legal.
- Do not put secrets or live credentials in the tree.

## Graft and docs

Start at `README.md`, then `specs/001-zzthat-apps/spec.md`.
Do not copy a code map into this file.

## Escalation

If CI, tests, or validators look wrong (missing coverage, silent skips, green-but-inert checks, or a suite that contradicts the spec):

1. Open a defect issue with label `bug`.
2. Do not patch tests, fixtures, or CI to force green.
3. Do not weaken an assertion to match a broken implementation.

A red honest check is better than a green lie.

## Team context

Product rules live in `Zero-State-LLC/zzthis`. Read that repo. Do not push to it from this repo's work.

## Skills

See the `## Workflows` section in `specs/001-zzthat-apps/spec.md`.
Defaults: `anti-slop-code`, `production-systems`, `google-developer-style`.

## Size

Keep this file short (about 80 lines; hard ceiling 12 KB).

## Upstream scope control

zzThis is the product-scope authority. Before turning a new upstream idea into native-app work, verify its capability bundle and target release at the pinned zzThis commit. Only ACTIVE bundles create implementation work. QUEUED, SHADOW, and RESEARCH items may be documented or accommodated by a non-behavioral interface only when doing so does not expand the current release. Do not infer implementation authority from marketing copy, a future-looking spec paragraph, or a Michael note by itself. After an upstream bundle is promoted, advance the zzThis pin through the normal sync flow before implementing it here.
