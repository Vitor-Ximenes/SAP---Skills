#!/usr/bin/env bash
set -euo pipefail

# Generate hardened SAP dependency security configuration candidates from templates.
# Defaults to dry-run output on stdout. Pass --write to write a selected
# candidate into an output directory after manual review.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TEMPLATE_DIR="${ROOT_DIR}/templates"

usage() {
  cat <<USAGE
Usage: $(basename "$0") [--write] <target> [output-dir]

Package-manager targets:
  npm   -> .npmrc
  bun   -> bunfig.toml
  pnpm  -> pnpm-workspace.yaml
  yarn  -> .yarnrc.yml
  deno  -> deno.json (template-free fallback)

SAP targets:
  mcp       -> sap-mcp-config.candidate.md
  maven     -> maven-security-fragment.md
  gradle    -> gradle-security-fragment.md
  python    -> python-security.md
  container -> container-trivy.yml
  btp       -> btp-cf-mbt-review.md
  abap      -> abap-gcts-review.md

Default mode prints the candidate to stdout and does not write files.
Use --write only after reviewing the candidate output.
USAGE
}

write_mode=0
if [[ ${1:-} == "--write" ]]; then
  write_mode=1
  shift
fi

if [[ ${1:-} == "" || ${1:-} == "-h" || ${1:-} == "--help" ]]; then
  usage
  exit 0
fi

target_name="${1:-}"
out_dir="${2:-.}"

emit_template() {
  local template_file="$1"
  local output_file="$2"
  local template_path="${TEMPLATE_DIR}/${template_file}"

  if [[ ! -f "$template_path" ]]; then
    echo "Error: template not found: $template_path" >&2
    exit 1
  fi

  if [[ "$write_mode" -eq 1 ]]; then
    mkdir -p "$out_dir"
    cp "$template_path" "$out_dir/$output_file"
    echo "Generated: $out_dir/$output_file"
    echo "Tip: review generated values before committing."
  else
    echo "# Candidate output: $output_file"
    echo "# Source template: $template_file"
    echo ""
    cat "$template_path"
  fi
}

emit_content() {
  local output_file="$1"
  local content="$2"

  if [[ "$write_mode" -eq 1 ]]; then
    mkdir -p "$out_dir"
    printf "%s\n" "$content" > "$out_dir/$output_file"
    echo "Generated: $out_dir/$output_file"
    echo "Tip: review generated values before committing."
  else
    echo "# Candidate output: $output_file"
    echo ""
    printf "%s\n" "$content"
  fi
}

case "$target_name" in
  npm|bun|pnpm|yarn)
    case "$target_name" in
      npm)
        template_file="npmrc-security.tmpl"
        output_file=".npmrc"
        ;;
      bun)
        template_file="bunfig-security.tmpl"
        output_file="bunfig.toml"
        ;;
      pnpm)
        template_file="pnpm-workspace-security.tmpl"
        output_file="pnpm-workspace.yaml"
        ;;
      yarn)
        template_file="yarnrc-security.tmpl"
        output_file=".yarnrc.yml"
        ;;
    esac

    emit_template "$template_file" "$output_file"
    ;;

  deno)
    emit_content "deno.json" '{
  "nodeModulesDir": "auto",
  "vendor": true
}'
    ;;

  mcp)
    emit_template "sap-mcp-config.tmpl" "sap-mcp-config.candidate.md"
    ;;
  maven)
    emit_template "maven-security.tmpl" "maven-security-fragment.md"
    ;;
  gradle)
    emit_template "gradle-security.tmpl" "gradle-security-fragment.md"
    ;;
  python)
    emit_template "python-security.tmpl" "python-security.md"
    ;;
  container)
    emit_template "container-trivy.tmpl" "container-trivy.yml"
    ;;
  btp)
    emit_template "btp-cf-mbt-review.tmpl" "btp-cf-mbt-review.md"
    ;;
  abap)
    emit_template "abap-gcts-review.tmpl" "abap-gcts-review.md"
    ;;

  *)
    echo "Unsupported target: $target_name" >&2
    usage >&2
    exit 1
    ;;
esac

exit 0
