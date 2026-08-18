#!/usr/bin/env bash
#
# Forward the staging repository this build just uploaded to the Central Portal.
#
# `./gradlew publish` uploads through the OSSRH Staging API, which leaves the artifacts in an OPEN
# staging repository. That is NOT a Portal deployment: it does not appear on
# https://central.sonatype.com/publishing/deployments, and nothing can be published from it. The
# release therefore looks entirely green while shipping nothing. This script is the bridge.
#
# IT MUST RUN ON THE MACHINE THAT DID THE UPLOAD. The API keys the default repository by
# (token user, source IP), so calling this from anywhere else — a laptop, a second job, a rerun on a
# fresh runner — answers `No repository found for <user>/<ip>/<namespace>--default-repository` even
# though the repository plainly exists. That is why this runs inside `publishCmd`, in the same step
# and on the same runner as the Gradle upload, rather than as a following workflow step.
#
# The publishing type is `user_managed` on purpose and is set here rather than left to the
# namespace default: the deployment lands VALIDATED and waits for a human to press Publish, which is
# the last point at which a release can still be dropped. Once it is published the version is
# immutable and there is no yank. Change the query parameter to `automatic` to make this workflow
# the last manual step. See .rules/RELEASE.md.
set -euo pipefail

NAMESPACE="${1:?usage: promote-central.sh <namespace>}"
: "${CENTRAL_TOKEN_USERNAME:?CENTRAL_TOKEN_USERNAME is not set}"
: "${CENTRAL_TOKEN_PASSWORD:?CENTRAL_TOKEN_PASSWORD is not set}"

API="https://ossrh-staging-api.central.sonatype.com"

# The Portal takes base64(user:password) as a BEARER token, which is not the same thing as HTTP
# Basic auth — curl's --user would send `Basic` and be rejected. `tr -d` because base64 wraps at 76
# columns on some platforms and a header with an embedded newline authenticates as nothing.
# Never echoed: Actions masks the two secrets, but it cannot mask a value derived from them.
AUTH="$(printf '%s:%s' "$CENTRAL_TOKEN_USERNAME" "$CENTRAL_TOKEN_PASSWORD" | base64 | tr -d '\n')"

echo "Promoting the ${NAMESPACE} staging repository to the Central Portal"

# --fail-with-body: exit non-zero on 4xx/5xx *and* still print the body, because this API explains
# itself in the body and a bare exit code here would send you looking in the wrong place.
curl --silent --show-error --fail-with-body --request POST \
    --header "Authorization: Bearer ${AUTH}" \
    "${API}/manual/upload/defaultRepository/${NAMESPACE}?publishing_type=user_managed"
echo

echo "Forwarded. Publish it at https://central.sonatype.com/publishing/deployments"
