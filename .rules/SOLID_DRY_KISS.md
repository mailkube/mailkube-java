# Engineering Standards: SOLID · DRY · KISS · Coverage · Docs

These are **enforced by CI** — a PR that violates them cannot merge. This file tells you the exact
thresholds and how to satisfy each gate locally *before* pushing.

## The gates

| Gate | Rule | Enforced by |
|---|---|---|
| **Coverage** | ≥ 90% **line and branch** | JaCoCo `jacocoTestCoverageVerification`, which `check` depends on |
| **DRY** | ≤ 1% duplicated code | `jscpd` (the `dry` CI job) |
| **KISS** | cyclomatic ≤ 10, cognitive ≤ 20 per method | PMD `config/pmd/ruleset.xml` |
| **Documentation** | every public type and method has Javadoc | PMD `CommentRequired`, plus `javadoc -Xdoclint` |
| **Strict analysis** | no javac warnings at all | `-Xlint:all -Werror` |
| **SOLID** | see below — approximated by lint + review | PMD + `module-info.java` + PR checklist |
| **Formatting** | palantir-java-format clean | `./gradlew spotlessCheck` |
| **Publish readiness** | the POM and artifacts Maven Central requires | `./gradlew validatePublication` |

> **`check` runs the coverage gate only because it is told to.** `tasks.check { dependsOn(jacocoTestCoverageVerification) }`
> is not automatic in Gradle; without it, `check` produces the report and enforces nothing.

> **A PMD ruleset that fails to load does not fail the build.** It prints
> `Cannot load ruleset ...` then `No files to analyze`, and the build goes green with the gate
> switched off. After editing the ruleset, read the `pmdMain` output.

## Run the gates locally

```bash
./gradlew spotlessApply                      # format
./gradlew spotlessCheck                      # formatting gate
./gradlew check                              # pmdMain + pmdTest + test + coverage gate
./gradlew javadoc                            # documentation builds
npx --yes jscpd@4 --config .jscpd.json .     # duplication (DRY) gate
./scripts/check-rule-index.sh                # every .rules/*.md indexed in AGENTS.md
./gradlew validatePublication                # publish readiness (see below)
```

> **`validatePublication` is not part of `check`, on purpose.** It builds the javadoc jar, which
> CI already builds in its own step. It is a separate gate because the failures it catches happen
> *after* semantic-release has pushed the tag — `publishCmd` publishes at the end of the release —
> and a Central rejection there is the one release failure that cannot simply be re-run.

**If you do not have a JDK 25**, the Gradle toolchain will download a matching one
itself, provided your environment has a toolchain resolver.

## SOLID, concretely (paradigm-neutral guidance)

SOLID is not a single lint rule; keep these in mind and confirm them in the PR checklist:

- **S**ingle responsibility — a class/method does one thing; if you need "and" to describe it, split it.
- **O**pen/closed — extend by adding a class or a builder method, not by editing a stable call site.
- **L**iskov — an injected `HttpClient` or `SendTransport` honours the documented contract.
- **I**nterface segregation — a resource depends on the narrowest interface its verbs need. A new
  capability adds an interface; it never widens an existing one. PMD's `UnusedFormalParameter`
  catches the cheap version of this.
- **D**ependency inversion — the `HttpClient` is injected through the builder, never constructed
  inside a resource. `module-info.java` makes the direction of the dependency a compile error rather
  than a review comment.

## Requesting a waiver

If a threshold is genuinely wrong for a specific line, add a **scoped, commented** suppression
(e.g. `@SuppressWarnings("PMD.CyclomaticComplexity") // flat dispatch table, no nesting`) and call it
out in the PR. Blanket relaxations (lowering the coverage floor, removing a rule from the ruleset)
require maintainer sign-off. Every relaxation already in the ruleset carries its reason; add yours
the same way or not at all.
