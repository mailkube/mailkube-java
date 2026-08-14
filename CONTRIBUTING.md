# Contributing to mailkube-java

Thanks for helping improve **mailkube-java**, a [mailkube](https://mailkube.com) SDK.
Contributions of all kinds are welcome: bug reports, fixes, docs, and features.

By contributing you agree that your contributions are licensed under the project's
[Apache License 2.0](LICENSE) (inbound = outbound). **No CLA and no sign-off are required.**
Please also read our [Code of Conduct](CODE_OF_CONDUCT.md).

## Development setup

Requires a **JDK 25** and Node.js (for the `jscpd` duplication check). Gradle itself comes
from the committed wrapper — do not install one.

```bash
git clone https://github.com/mailkube/mailkube-java
cd mailkube-java

./gradlew build
```

If you do not have a JDK 25, let the Gradle toolchain fetch one — it will, given a
toolchain resolver.

## Quality gates

Every change must pass the same checks CI runs (see [.rules/SOLID_DRY_KISS.md](.rules/SOLID_DRY_KISS.md)):

```bash
./gradlew spotlessApply                      # format (palantir-java-format)
./gradlew spotlessCheck                      # formatting gate
./gradlew check                              # PMD + tests + the 90% line/branch coverage gate
./gradlew javadoc                            # documentation builds
npx --yes jscpd@4 --config .jscpd.json .     # duplication (DRY) gate, blocks at > 1%
./scripts/check-rule-index.sh                # every .rules/*.md indexed in AGENTS.md
```

Two things that trip people up, both documented in [.rules/SDK_DESIGN.md](.rules/SDK_DESIGN.md):
`check` runs the coverage gate only because it is explicitly told to, and a PMD ruleset that fails
to load leaves the build green with the gate switched off.

## Commit & PR conventions

This project follows **[Conventional Commits](https://www.conventionalcommits.org/)**. A CI check
enforces the **PR title** (PRs are **squash-merged** using it), and it drives releases: only
`feat:`, `fix:`, and `perf:` cut a new version. See [.rules/RELEASE.md](.rules/RELEASE.md).

Suggested scopes: `client`, `models`, `ci`, `deps`, `docs`.

```
feat(client): add retry with exponential backoff
fix(models): correct optional field serialization
docs: document the pagination helper
```

## Reporting bugs / requesting features

Open an issue using the templates. For **security vulnerabilities**, do not open a public
issue — follow [SECURITY.md](SECURITY.md) instead.
