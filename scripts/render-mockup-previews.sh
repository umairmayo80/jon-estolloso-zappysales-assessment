#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mockup_dir="$repo_root/design/mockups"
output_dir="$repo_root/design/previews"
chrome_bin="${CHROME_BIN:-google-chrome}"
chrome_profile="$(mktemp -d "${TMPDIR:-/tmp}/profile-directory-chrome.XXXXXX")"

cleanup() {
  rm -rf -- "$chrome_profile"
}
trap cleanup EXIT

if ! command -v "$chrome_bin" >/dev/null 2>&1; then
  echo "Chrome is required to render mockup previews. Set CHROME_BIN or install Google Chrome/Chromium." >&2
  exit 127
fi

if [[ ! -d "$mockup_dir" ]]; then
  echo "Mockup directory not found: $mockup_dir" >&2
  exit 1
fi

mkdir -p "$output_dir"
shopt -s nullglob
mockups=("$mockup_dir"/*.html)

if (( ${#mockups[@]} == 0 )); then
  echo "No mockup HTML files found in $mockup_dir" >&2
  exit 1
fi

for mockup in "${mockups[@]}"; do
  name="$(basename "${mockup%.html}")"
  "$chrome_bin" \
    --headless=new \
    --no-sandbox \
    --disable-dev-shm-usage \
    --disable-gpu \
    --hide-scrollbars \
    --user-data-dir="$chrome_profile" \
    --window-size=1440,4000 \
    --screenshot="$output_dir/${name}-desktop.png" \
    "file://$mockup"
  "$chrome_bin" \
    --headless=new \
    --no-sandbox \
    --disable-dev-shm-usage \
    --disable-gpu \
    --hide-scrollbars \
    --user-data-dir="$chrome_profile" \
    --window-size=390,3400 \
    --screenshot="$output_dir/${name}-mobile.png" \
    "file://$mockup"
done

echo "Rendered ${#mockups[@]} mockup(s) to $output_dir"
