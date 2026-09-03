#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
default_source="$repo_root/docs/submission/Tariq_Sardar_Umair_AssessmentForFullStackDeveloper_2026-09-03.md"

usage() {
  cat <<'EOF'
Usage:
  scripts/render-submission-pdf.sh [source-markdown] [output-directory]
  scripts/render-submission-pdf.sh --check-tools

Requires Poppler's pdftoppm plus either:
  - native pandoc and XeLaTeX, or
  - the locally available pandoc/latex:latest Docker image.

On Debian/Ubuntu, install the native tools with:
  sudo apt-get install pandoc texlive-xetex poppler-utils fonts-dejavu

To prepare the container fallback without changing the system toolchain:
  docker pull pandoc/latex:latest
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

has_native_renderer=1
for tool in pandoc xelatex; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    has_native_renderer=0
  fi
done

has_container_renderer=0
if command -v docker >/dev/null 2>&1 && docker image inspect pandoc/latex:latest >/dev/null 2>&1; then
  has_container_renderer=1
fi

has_rasterizer=1
if ! command -v pdftoppm >/dev/null 2>&1; then
  has_rasterizer=0
fi

if [[ "${1:-}" == "--check-tools" ]]; then
  if (( ! has_rasterizer )) || (( ! has_native_renderer && ! has_container_renderer )); then
    if (( ! has_rasterizer )); then
      echo "Missing required tool: pdftoppm" >&2
    fi
    if (( ! has_native_renderer && ! has_container_renderer )); then
      echo "Missing PDF renderer: install native pandoc + xelatex or pull pandoc/latex:latest." >&2
    fi
    usage >&2
    exit 1
  fi
  if (( has_native_renderer )); then
    echo "Native PDF toolchain is available."
  else
    echo "Container PDF toolchain is available."
  fi
  exit 0
fi

if (( ! has_rasterizer )) || (( ! has_native_renderer && ! has_container_renderer )); then
  if (( ! has_rasterizer )); then
    echo "Missing required tool: pdftoppm" >&2
  fi
  if (( ! has_native_renderer && ! has_container_renderer )); then
    echo "Missing PDF renderer: install native pandoc + xelatex or pull pandoc/latex:latest." >&2
  fi
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

if (( has_native_renderer )); then
  pandoc "$source_file" \
    --from=markdown+yaml_metadata_block \
    --standalone \
    --pdf-engine=xelatex \
    --include-in-header="$repo_root/docs/submission/pandoc-header.tex" \
    --resource-path="$repo_root:$repo_root/docs:$repo_root/docs/submission:$repo_root/docs/submission/assets:$repo_root/design" \
    --toc \
    --number-sections \
    --variable=mainfont:"DejaVu Sans" \
    --variable=monofont:"DejaVu Sans Mono" \
    --variable=linkcolor:blue \
    --output="$pdf_file"
else
  relative_source="${source_file#"$repo_root"/}"
  relative_output="${pdf_file#"$repo_root"/}"
  docker run --rm --network none \
    --user "$(id -u):$(id -g)" \
    --env HOME=/tmp \
    --volume "$repo_root:/data" \
    --workdir /data \
    pandoc/latex:latest \
    "/data/$relative_source" \
    --from=markdown+yaml_metadata_block \
    --standalone \
    --pdf-engine=xelatex \
    --include-in-header=/data/docs/submission/pandoc-header.tex \
    --resource-path=/data:/data/docs:/data/docs/submission:/data/docs/submission/assets:/data/design \
    --toc \
    --number-sections \
    --variable=linkcolor:blue \
    --output="/data/$relative_output"
fi

pdftoppm -png -r 144 "$pdf_file" "$preview_dir/page"

if command -v pdfinfo >/dev/null 2>&1; then
  page_count="$(pdfinfo "$pdf_file" | awk -F: '/^Pages:/ {gsub(/^[[:space:]]+/, "", $2); print $2}')"
  echo "Generated $pdf_file (${page_count:-unknown} page(s)); inspect $preview_dir"
else
  echo "Generated $pdf_file; inspect $preview_dir"
fi
