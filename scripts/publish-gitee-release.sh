#!/usr/bin/env bash
set -euo pipefail

# Publishes the APK built by Gitee Go directly to a Gitee Release.
# Required environment variables: GITEE_TOKEN, GITEE_UPDATE_REPOSITORY,
# RELEASE_BUILD_NUMBER. The signing variables are consumed by Gradle itself.

: "${GITEE_TOKEN:?Set GITEE_TOKEN as a protected Gitee Go secret.}"
: "${GITEE_UPDATE_REPOSITORY:?Set GITEE_UPDATE_REPOSITORY to owner/repository.}"
: "${RELEASE_BUILD_NUMBER:?Set RELEASE_BUILD_NUMBER before invoking this script.}"

apk_dir="app/build/outputs/apk/release"
apk_path="$apk_dir/app-release.apk"
[[ -s "$apk_path" ]] || { echo "Release APK was not found: $apk_path" >&2; exit 1; }

version_code=$((20000000 + RELEASE_BUILD_NUMBER))
tag="v2.0.${RELEASE_BUILD_NUMBER}"
release_version_name="${RELEASE_VERSION_NAME:-2.0.32}"
sha256=$(sha256sum "$apk_path" | awk '{print $1}')
printf '%s  %s\n' "$sha256" "app-release.apk" > "$apk_dir/app-release.apk.sha256"

printf '{"versionCode":%s,"versionName":"%s","apkName":"app-release.apk","sha256":"%s"}\n' \
  "$version_code" "$release_version_name" "$sha256" > "$apk_dir/update.json"

api="https://gitee.com/api/v5/repos/${GITEE_UPDATE_REPOSITORY}/releases"
target_branch="${GITEE_TARGET_BRANCH:-main}"
release_json=$(curl --fail-with-body --silent --show-error --request POST "$api" \
  --header "Authorization: Bearer ${GITEE_TOKEN}" \
  --form "access_token=${GITEE_TOKEN}" \
  --form "tag_name=${tag}" \
  --form "name=HuangguoPlayer ${release_version_name}" \
  --form "body=自动构建发布。 versionName: ${release_version_name}, versionCode: ${version_code}" \
  --form "target_commitish=${target_branch}")
release_id=$(printf '%s' "$release_json" \
  | grep -oE '"id"[[:space:]]*:[[:space:]]*[0-9]+' \
  | head -n 1 \
  | tr -cd '0-9')
[[ "$release_id" =~ ^[0-9]+$ ]] || {
  echo "Gitee did not return a valid release id." >&2
  exit 1
}

for asset in app-release.apk app-release.apk.sha256 update.json; do
  curl --fail-with-body --silent --show-error --request POST "${api}/${release_id}/attach_files" \
    --header "Authorization: Bearer ${GITEE_TOKEN}" \
    --form "access_token=${GITEE_TOKEN}" \
    --form "file=@${apk_dir}/${asset}"
done

echo "Published: https://gitee.com/${GITEE_UPDATE_REPOSITORY}/releases/tag/${tag}"
