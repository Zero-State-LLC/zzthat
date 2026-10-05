# Contributing

1. Read [`AGENTS.md`](AGENTS.md), the [constitution](.specify/memory/constitution.md), and [spec 001](specs/001-zzthat-apps/spec.md) before changing product behavior.
2. Branch from `main` and open a pull request. Use the pull request template. [`CODEOWNERS`](.github/CODEOWNERS) names the review team.
3. Before you push, run `python3 scripts/lint-specs.py`.
4. User-facing copy says "demo", says "check word", and uses no em dashes.
5. Do not add design tokens, logos, or icons in this repo. Follow [design.md](specs/001-zzthat-apps/design.md). The one exception is the placeholder app icon that design.md says to compose once from the pinned zz code mark and commit, until Michael's artwork replaces it (ZQ25).
6. Do not add a resolver or a backend. The apps call the zzThis server.
7. Store submission, developer-account spend, and production credentials need Danny's yes.
8. Use the issue templates. New issues and pull requests are added to the [zzThat board](https://github.com/orgs/Zero-State-LLC/projects/25).
