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
./gradlew check                              # PMD (main, test, examples) + tests + coverage gate
./gradlew javadoc                            # documentation builds
npx --yes jscpd@4 --config .jscpd.json .     # duplication (DRY) gate, blocks at > 1%
npx --yes jscpd@4 --config .jscpd.examples.json examples/  # the same gate over examples/
./gradlew jar -Pversion=0.0.0-ci && javac -proc:none -Xlint:all -Werror \
  -d /tmp/ex -cp build/libs/mailkube-java-0.0.0-ci.jar examples/*.java   # examples compile
./scripts/check-rule-index.sh                # every .rules/*.md indexed in AGENTS.md
./gradlew validatePublication                # the POM/artifacts Maven Central requires
```

**`examples/` is compiled and linted.** It is runnable documentation, which is the reason, not an
exception to it: customers copy those files, and every defect the SDK certification run surfaced
lived there because no gate looked at it. `examples/` is not a Gradle source set, so `check` never
compiles it — CI's `examples` job runs javac against the built jar, and `pmdExamples` (wired into
`check`) covers complexity and dead code.

Spotless applies **whitespace rules only** there, and that is a hard tooling limit rather than a
preference: examples are JEP 512 compact source files, and palantir-java-format 2.97.0 — the
newest release — cannot parse them. Pointing the `java` block at `examples/` would turn
`spotlessCheck` permanently red with no upgrade available.

Two more traps to know about, both of which produce a GREEN build while checking nothing: PMD
cannot parse a **top-level field** in a compact source file (keep fields inside `main`), and its
Gradle task exits 0 while printing the ParseException. CI greps the output for exactly that.

Duplication over `examples/` is measured by a separate pass, `.jscpd.examples.json`, at
`minTokens: 100` instead of 50 — every example repeats the same scaffolding, and hoisting it into
a shared helper would make each file unreadable on its own. Coverage excludes examples, because
nothing in CI executes them.

Two things that trip people up, both documented in [.rules/SDK_DESIGN.md](.rules/SDK_DESIGN.md):
`check` runs the coverage gate only because it is explicitly told to, and a PMD ruleset that fails
to load leaves the build green with the gate switched off.

## Commit & PR conventions

This project follows **[Conventional Commits](https://www.conventionalcommits.org/)**. A CI check
enforces the **PR title** (PRs are **squash-merged** using it), and it drives releases: only
`feat:`, `fix:`, and `perf:` cut a new version. See [.rules/RELEASE.md](.rules/RELEASE.md).

Suggested scopes: `client`, `models`, `ci`, `deps`, `docs`.

**Maintainers setting up branch protection:** a required status check is matched on the **job**
name, not the workflow name and not the job id. The names to require are `PR-title` (from
`pr-title.yml`, whose job id is `conventional-title` — requiring *that* would block every PR
forever), plus `test`, `dry` and `docs` from `ci.yml`.

```
feat(client): add retry with exponential backoff
fix(models): correct optional field serialization
docs: document the pagination helper
```

## Reporting bugs / requesting features

Open an issue using the templates. For **security vulnerabilities**, do not open a public
issue — follow [SECURITY.md](SECURITY.md) instead.
