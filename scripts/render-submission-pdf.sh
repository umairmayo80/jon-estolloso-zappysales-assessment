#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
default_source="$repo_root/docs/submission/LastName_FirstName_AssessmentForFullStackDeveloper_2026-09-03.md"

usage() {
  cat <<'EOF'
Usage:
  scripts/render-submission-pdf.sh [source-markdown] [output-directory]
  scripts/render-submission-pdf.sh --check-tools

Requires pandoc, XeLaTeX, and Poppler's pdftoppm. On Debian/Ubuntu:
  sudo apt-get install pandoc texlive-xetex poppler-utils fonts-dejavu
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

missing=0
for tool in pandoc xelatex pdftoppm; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing required tool: $tool" >&2
    missing=1
  fi
done

if [[ "${1:-}" == "--check-tools" ]]; then
  if (( missing )); then
    usage >&2
    exit 1
  fi
  echo "PDF toolchain is available."
  exit 0
fi

if (( missing )); then
  usage >&2
  exit 127
fi

source_file="${1:-$default_source}"
output_dir="${2:-$repo_root/output/pdf}"

if [[ ! -f "$source_file" ]]; then
  echo "Submission source not found: $source_file" >&2
  exit 1
fi

source_file="$(realpath "$source_file")"
case "$source_file" in
  "$repo_root"/*) ;;
  *)
    echo "For safety, source Markdown must be inside this repository." >&2
    exit 1
    ;;
esac

mkdir -p "$output_dir"
output_dir="$(realpath "$output_dir")"
pdf_name="$(basename "${source_file%.md}").pdf"
pdf_file="$output_dir/$pdf_name"
preview_dir="$output_dir/previews/$(basename "${source_file%.md}")"
mkdir -p "$preview_dir"

pandoc "$source_file" \
  --from=markdown+yaml_metadata_block \
  --standalone \
  --pdf-engine=xelatex \
  --include-in-header="$repo_root/docs/submission/pandoc-header.tex" \
  --resource-path="$repo_root:$repo_root/docs:$repo_root/design" \
  --toc \
  --number-sections \
  --variable=mainfont:"DejaVu Sans" \
  --variable=monofont:"DejaVu Sans Mono" \
  --variable=linkcolor:blue \
  --output="$pdf_file"

pdftoppm -png -r 144 "$pdf_file" "$preview_dir/page"

if command -v pdfinfo >/dev/null 2>&1; then
  page_count="$(pdfinfo "$pdf_file" | awk -F: '/^Pages:/ {gsub(/^[[:space:]]+/, "", $2); print $2}')"
  echo "Generated $pdf_file (${page_count:-unknown} page(s)); inspect $preview_dir"
else
  echo "Generated $pdf_file; inspect $preview_dir"
fi
