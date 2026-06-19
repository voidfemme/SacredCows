#!/usr/bin/env bash
set -euo pipefail

if [ -z "${1:-}" ]; then
  echo "Usage: $0 <version>   (e.g. $0 4.1.0)" >&2
  exit 1
fi

version="$1"
tag="v${version}"
jar_path="build/libs/sacredcows-${version}.jar"
sha_path="${jar_path}.sha256"
notes_file="/tmp/release-notes-${version}.md"

extract_changelog() {
  local version="$1"
  local changelog="${2:-CHANGELOG.md}"
  awk -v ver="$version" '
    $0 == "## [" ver "]" { capturing = 1; print; next }
    capturing && /^## \[/ { exit }
    capturing { print }
  ' "$changelog"
}

# 1. Sanity-check git state
if [ -n "$(git status --porcelain)" ]; then
  echo "Error: working tree is not clean. Commit or stash first." >&2
  exit 1
fi
git log -1 --oneline

# 2. Verify gradle.properties matches the requested version
gradle_version=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
if [ "$gradle_version" != "$version" ]; then
  echo "Error: gradle.properties has mod_version=$gradle_version, expected $version" >&2
  exit 1
fi

# 3. Extract changelog entry early — fail fast if missing
extract_changelog "$version" CHANGELOG.md > "$notes_file"
if [ ! -s "$notes_file" ]; then
  echo "Error: no changelog entry found for version $version" >&2
  exit 1
fi

# 4. Build the jar
./gradlew clean build

# 5. Verify the jar exists
if [ ! -f "$jar_path" ]; then
  echo "Error: $jar_path not found after build" >&2
  exit 1
fi

# 6. Generate a checksum file
sha256sum "$jar_path" > "$sha_path"
echo "Built:"
cat "$sha_path"

# 7. Create the annotated git tag using the changelog entry as the message
git tag -a "$tag" -F "$notes_file"

# 8. Push the commit and the tag
git push origin main
git push origin "$tag"

# 9. Create the GitHub release with assets
gh release create "$tag" \
  "$jar_path" \
  "$sha_path" \
  --title "SacredCows ${version}" \
  --notes-file "$notes_file"

# 10. Verify
gh release view "$tag"
