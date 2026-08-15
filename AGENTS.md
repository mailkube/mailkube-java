# Project Rules

`mailkube-java` is a public (Apache-2.0) mailkube SDK published to Maven Central
as `com.mailkube:mailkube-java`. Load the relevant rule file from `.rules/` based on the task.

## Rule Index

> **Index every rule (required).** Every file in `.rules/` MUST have a row in the table below. When you
> add or rename a `.rules/` file, add or update its row in the **same change** — an unindexed rule is
> invisible, because this index is what drives progressive disclosure. The `docs` CI job (`scripts/check-rule-index.sh`)
> fails the build if `.rules/` and this index drift. This convention holds for every mailkube repo.

| Rule File | Load When |
|---|---|
| `.rules/SOLID_DRY_KISS.md` | Writing or changing any code — the enforced engineering standards (SOLID, DRY, KISS, coverage, docs) and how to run each gate locally. |
| `.rules/SDK_CONTRACT.md` | Adding a resource, verb, response model, paginated listing, or webhook event: the cross-SDK decisions (config, layering, naming, errors, pagination, webhooks) every mailkube SDK implements identically. Shared verbatim across every SDK; changes are made centrally. |
| `.rules/SDK_DESIGN.md` | The same tasks, for the **Java realization**: the module descriptor as the layering gate, the Java 25 floor and why it is load-bearing, the three non-obvious `HttpClient` rules, and why this SDK writes its own JSON. |
| `.rules/RELEASE.md` | Touching `release.yml`, `.releaserc.json`, `gradle.properties`, or the Maven Central publish flow. |
| `.rules/CI_GATES.md` | Adding, removing or weakening a CI job, or when a release fails after the tag was already pushed: why the publish-readiness, dependency-floor, example-compilation and release-permission gates exist. Shared verbatim across every mailkube repo; changes are made centrally. |

## Key Conventions (always apply)

- **Standard Gradle layout** — the library lives in `src/main/java/`, its tests in `src/test/java/`.
- **Gradle with the Kotlin DSL**, driven through the committed wrapper (`./gradlew`), never a system Gradle.
- **Spotless (palantir-java-format)** for formatting; **PMD** for complexity and docs; **JaCoCo** for coverage. All pinned exactly.
- **Line length ≤ 120**, which is what palantir-java-format emits.
- **Javadoc every public type and method**, with `@param` and `@return`. PMD's `CommentRequired` enforces it.
- **≥ 90% coverage, line + branch** — `check` depends on `jacocoTestCoverageVerification`; never lower the gate to make a change pass.
- **Max cyclomatic 10 / cognitive 20** — split, don't waive.
- **`-Xlint:all -Werror`** — a warning is a build failure. Do not suppress one without a comment saying why.
- **No duplication** — the `jscpd` gate blocks at > 1% duplicated code; extract shared logic.
- **Zero runtime dependencies.** This library is installed into other people's applications. Read `.rules/SDK_DESIGN.md` before adding the first one.
- **`internal` is not exported** by `module-info.java`, and must stay that way: it is what makes the layering a compile error rather than a review comment.
- **The version is never a literal** — `gradle.properties` holds it, the jar manifest carries it, `Version` reads it back.
- **Conventional Commits** for PR titles (squash-merged); only `feat:`/`fix:`/`perf:` cut a release.
- **No secrets in the repo** — local config lives in a git-ignored `.env`, excluded from the built jar.
- **Keep the `README` current** with user-visible changes. There is no `CHANGELOG.md`; the release
  notes on the GitHub Releases page are the changelog (see `.rules/RELEASE.md`).
