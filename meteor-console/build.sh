#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
console_directory="$PWD"

usage() {
    cat <<'EOF'
Usage:
  ./build.sh web [gradle options...]
  ./build.sh web-test [playwright options...]

Commands:
  web    Build console frontend and copy it into backend resources.
  web-test    Run browser regression tests against the Java test fixture.
  help   Show this help message.

Examples:
  ./build.sh web
  ./build.sh web --rerun-tasks
  ./build.sh web-test --grep 'provider debugging'
EOF
}

target="${1:-help}"
if [[ "$#" -gt 0 ]]; then
    shift
fi

case "$target" in
    web|web-test)
        export npm_config_cache="$console_directory/build/cache/npm"
        export PLAYWRIGHT_BROWSERS_PATH="${PLAYWRIGHT_BROWSERS_PATH:-$console_directory/build/cache/playwright}"
        export TMPDIR="$console_directory/build/tmp/node" TMP="$console_directory/build/tmp/node" TEMP="$console_directory/build/tmp/node"
        mkdir -p "$npm_config_cache" "$TMPDIR"
        cd src/web
        if [[ "$target" == web ]]; then
            npm ci --no-audit --no-fund
            npm run build
            cd "$console_directory/.."
            ./gradlew -PincludeTestProjects=false :meteor-console:processResources "$@"
        else
            npm run test:e2e -- "$@"
        fi
        ;;
    help|-h|--help)
        usage
        ;;
    *)
        echo "Unknown console build target: $target" >&2
        usage >&2
        exit 1
        ;;
esac
