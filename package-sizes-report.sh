#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="${1:-markdown}"

find_artifacts() {
  find "${ROOT_DIR}" -type f \
    \( -name "*-release.aar" -o -name "*-release.jar" \) \
    -path "*/build/outputs/*"
}

file_bytes() {
  local f="$1"
  wc -c < "${f}" | tr -d ' '
}

to_kb() {
  local bytes="$1"
  awk "BEGIN { printf \"%.1f\", ${bytes}/1024 }"
}

module_name_from_path() {
  local path="$1"
  local rel
  rel="${path#${ROOT_DIR}/}"
  echo "${rel}" | awk -F'/build/outputs/' '{print $1}'
}

artifact_name_from_path() {
  local path="$1"
  basename "${path}"
}

build_release_artifacts() {
  echo "==> Building release artifacts" >&2
  ./gradlew clean assembleRelease >/dev/null
}

emit_records() {
  while IFS= read -r artifact; do
    local bytes kb module base
    bytes="$(file_bytes "${artifact}")"
    kb="$(to_kb "${bytes}")"
    module="$(module_name_from_path "${artifact}")"
    base="$(artifact_name_from_path "${artifact}")"

    printf '%s\t%s\t%s\t%s\n' "${module}" "${bytes}" "${kb}" "${base}"
  done < <(find_artifacts)
}

print_plain_text() {
  printf "%-45s %12s %s\n" "MODULE" "SIZE(KB)" "ARTIFACT"
  printf "%-45s %12s %s\n" "---------------------------------------------" "------------" "------------------------------------"

  while IFS=$'\t' read -r module bytes kb base; do
    printf "%-45s %12s %s\n" "${module}" "${kb}" "${base}"
  done < <(emit_records)
}

print_markdown() {
  cat <<'EOF'
# Android SDK package size report

This report shows the size of each generated **release AAR/JAR artifact**.

## What this measures

- Exact file size of each generated release artifact
- Useful for comparing the built artifacts with the files on disk

## Measurement method

1. Build release artifacts with `assembleRelease`
2. Locate generated `*-release.aar` and `*-release.jar` files under `build/outputs`
3. Measure each file directly in bytes
4. Convert bytes to KB using `bytes / 1024`

This reflects the built artifact file size on disk.

## Package size summary

| Module | Size (KB) | Artifact |
|---|---:|---|
EOF

  while IFS=$'\t' read -r module bytes kb base; do
    printf '| `%s` | %s | `%s` |\n' "${module}" "${kb}" "${base}"
  done < <(emit_records)

  cat <<'EOF'

EOF
}

main() {
  build_release_artifacts

  case "${MODE}" in
    text)
      print_plain_text
      ;;
    markdown|md)
      print_markdown
      ;;
    *)
      echo "Usage: $0 [markdown|text]" >&2
      exit 1
      ;;
  esac
}

main "$@"