# Release & Publishing

Load this when touching `release.yml`, `.releaserc.json`, `gradle.properties`, or the Maven Central
publish flow.

## The contract

1. **Conventional Commits drive the version.** On push to `main`, `semantic-release` reads the commit
   history since the last tag: `fix:` → patch, `feat:` → minor, `feat!:`/`BREAKING CHANGE:` → major.
   `perf:` also releases. Anything else (`chore`, `docs`, `ci`, `refactor`, `test`) does **not** release.
2. **It creates the tag `vX.Y.Z` and the GitHub Release, and commits nothing.** No
   `chore(release):` commit, no `CHANGELOG.md`, no version bump landed in the tree. See "Why nothing
   is committed back to `main`".
3. **The tag IS the version.** `@semantic-release/exec`'s `publishCmd` runs
   `./gradlew publish -Pversion=X.Y.Z`. Gradle writes that version into the jar manifest, and
   `Version.current()` reads it back for the User-Agent, so the version on the wire equals the
   released version by construction.
   The `version=` line in `gradle.properties` is a permanent `0.0.0` placeholder. A local build
   therefore produces `0.0.0` and a published artifact carries the real version: **that is
   intended.** Do not "fix" it by hardcoding a number, in `gradle.properties` or in Java source.
4. **`publishCmd` uploads the signed artifacts**, and only when the commits actually warrant a
   release. That placement is deliberate: a workflow step after `semantic-release` runs on *every*
   push to `main`, including the ones that release nothing, and would upload the `0.0.0` placeholder
   to Central — which cannot be undone.

## `publish` needs a repository, and its silence is the failure mode

Gradle's `maven-publish` needs two things: a **publication** (what to upload) and a **repository**
(where). With a publication and no repository, `./gradlew publish` has nothing to do, does nothing,
and **exits 0** — a release that goes green and ships no artifact. Nothing warns you.

`build.gradle.kts` therefore declares one, and two details about it are load-bearing:

- **The URL is the Portal's OSSRH Staging API**
  (`https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`). The
  Central Portal has no plain Maven endpoint; this one accepts an ordinary Maven deploy and turns
  it into a Portal deployment.
- **The repository is named `central` on purpose.** `PasswordCredentials` resolves
  `<repositoryName>Username` / `<repositoryName>Password` project properties, so the name is what
  makes `ORG_GRADLE_PROJECT_centralUsername` / `centralPassword` in `release.yml` reach it. Rename
  the repository and the credentials are silently not found.

**A successful upload is not yet a release.** The Portal's default publishing mode is
`user_managed`: the deployment lands in the Portal and waits for you to press Publish. Set the
namespace to **automatic** publishing in the Portal UI if you want the workflow to be the last
manual step. Either way, check the Portal after the first release rather than assuming a green job
means the artifact is on Central.

## Why nothing is committed back to `main`

`main` is covered by a ruleset requiring a pull request and the gated checks. A `chore(release):`
commit pushed straight to `main` by the workflow violates it, and the obvious fix does not exist:
**`github-actions[bot]` cannot be added to a ruleset bypass list.** Bypass is available to admins,
the maintain/write role, teams, GitHub Apps and Dependabot, and the built-in Actions identity is none
of those. Making the commit work would mean introducing a separate identity — a GitHub App or a
deploy key — purely to write a version number that the tag already carries.

So `.releaserc.json` loads neither `@semantic-release/git` nor `@semantic-release/changelog`, and
nothing is written into the tree at all: the version travels as a `-P` property from the tag to
Gradle. **The generated release notes are the changelog**; there is no `CHANGELOG.md` in this repo.

## Maven Central is the outlier, and it is worth saying out loud

**Every other registry in this SDK family supports OIDC trusted publishing. Maven Central does
not.** PyPI, npm and RubyGems each let a workflow exchange a short-lived identity token for a
credential, so those repos store no secrets. Go needs nothing at all.

This repo needs **two long-lived secrets**, and there is no way around it:

| Secret | What it is |
|---|---|
| `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD` | A user token from the Central Portal (Account → Generate User Token). Not your portal password. |
| `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` | An ASCII-armoured private key whose **public** half is published to a keyserver. Central rejects unsigned artifacts. |

Treat them as the highest-value secrets in the repo: rotate on any suspicion, and never echo them
in a workflow step.

## Required one-time setup

- **Verify the `com.mailkube` namespace.** The Central Portal proves ownership of a
  reversed domain via a **DNS TXT record** on that domain. This has real lead time and blocks the
  first publish; start it before you need it.
- **GitHub environment `release`** (Settings → Environments) with protection rules; the `release`
  job runs in it and holds the four secrets above.
- **Publish the GPG public key** to `keyserver.ubuntu.com` (or another Central accepts), or
  verification fails at upload with an unhelpful message.
- **Decide the namespace's publishing mode** in the Portal: `user_managed` (the default, you
  press Publish) or `automatic`. See the section above.

## Do not

- Do not bump the `version=` line, move tags, or add a `CHANGELOG.md`, `@semantic-release/git`
  or `@semantic-release/changelog`. All of those reintroduce the commit to `main` that this
  setup exists to avoid.
- Do not add a `version` literal anywhere in Java source. `Version` reads the manifest for a reason.
- Do not move `./gradlew publish` out of `publishCmd` into a workflow step — see contract point 4.
- Do not remove the `repositories` block or rename the `central` repository — both failures are
  silent, and the second one only shows up as an authentication error at release time.
- Do not publish a snapshot to Central; it does not accept them.
- Do not gate `release.yml` on anything weaker than the full `ci.yml` (`test` + `dry` + `docs`).
