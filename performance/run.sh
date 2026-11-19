#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
performance_directory="$PWD"
repository_directory="$(cd .. && pwd)"
command="${1:-help}"
if [[ $# -gt 0 ]]; then shift; fi
case "$command" in
    local|server|client) ;;
    help|-h|--help)
        cat <<'EOF'
Usage:
  ./performance/run.sh local  [options]
  ./performance/run.sh server [options]
  ./performance/run.sh client --address rsf://host:port [options]

Options:
  --protocol NAME                            Default: rsf
    rsf, dubbo, grpc, grpc-web, connect, triple, hessian, hprose,
    jsonrpc, xmlrpc, soap, thrift, thrift-http, oncrpc, oncrpc-udp
  --host 127.0.0.1 --port 0                   Server bind address
  --concurrency 16 --payload 1024             Client concurrency and payload bytes
  --client-pool-size 1                        HTTP connections per target address
  --http-version 1|2                         Default: 2 for grpc/triple, 1 for other HTTP protocols
  --tcp-no-delay true|false                  Override the RPC TCP_NODELAY default (true)
  --transport-workers N --task-workers N      Optional network worker overrides (1-256)
  --business-threads N                       Optional fixed business pool (1-256)
  --warmup 5 --duration 30 --timeout 3000      Seconds, seconds and milliseconds

Reports, logs and isolated process directories are written under performance/build/runs/.
JAVA_OPTS can override the default heap/CPU settings or enable JFR.
METEOR_NETA_SOURCE can name an absolute Neta checkout for an explicit composite build.
EOF
        exit 0 ;;
    *) echo "Unknown command: $command" >&2; exit 2 ;;
esac
for option in "$@"; do
    case "$option" in
        --work-dir|--ready-file|--output)
            echo "$option is managed by run.sh; use the installed launcher for custom paths." >&2; exit 2 ;;
        --address)
            if [[ "$command" == local ]]; then echo 'local chooses its own server address.' >&2; exit 2; fi ;;
    esac
done

mkdir -p build/runs
run_directory="$(mktemp -d "$performance_directory/build/runs/run-XXXXXXXX")"
export TMPDIR="$run_directory/tmp" TMP="$run_directory/tmp" TEMP="$run_directory/tmp"
mkdir -p "$TMPDIR"
export METEOR_PERFORMANCE_REVISION="$(git -C "$repository_directory" rev-parse HEAD)"
if [[ -n "$(git -C "$repository_directory" status --porcelain)" ]]; then
    export METEOR_PERFORMANCE_DIRTY=true
else
    export METEOR_PERFORMANCE_DIRTY=false
fi
echo "Run directory: $run_directory"
gradle_arguments=(-p "$repository_directory" -PincludeTestProjects=false -PincludePerformance=true)
if [[ -n "${METEOR_NETA_SOURCE:-}" ]]; then
    gradle_arguments+=(--include-build "$METEOR_NETA_SOURCE")
fi
if ! "$repository_directory/gradlew" "${gradle_arguments[@]}" \
    :performance:installDist --console=plain > "$run_directory/build.log" 2>&1; then
    cat "$run_directory/build.log" >&2
    exit 1
fi
launcher="$performance_directory/build/install/performance/bin/performance"
if [[ "$command" != local ]]; then
    exec "$launcher" "$command" "$@" --work-dir "$run_directory/$command"
fi

server_pid=
client_pid=
cleanup() {
    if [[ -n "$client_pid" ]]; then
        kill -TERM "$client_pid" 2>/dev/null || true
        wait "$client_pid" 2>/dev/null || true
    fi
    if [[ -n "$server_pid" ]]; then
        kill -TERM "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"$launcher" server "$@" --work-dir "$run_directory/server" --ready-file "$run_directory/ready.txt" \
    > "$run_directory/server.log" 2>&1 &
server_pid=$!
for ((attempt=0; attempt<300; attempt++)); do
    if [[ -s "$run_directory/ready.txt" ]]; then break; fi
    if ! kill -0 "$server_pid" 2>/dev/null; then cat "$run_directory/server.log" >&2; exit 1; fi
    sleep 0.1
done
if [[ ! -s "$run_directory/ready.txt" ]]; then echo 'Server readiness timed out.' >&2; exit 1; fi
read -r address < "$run_directory/ready.txt"
"$launcher" client "$@" --address "$address" --work-dir "$run_directory/client" --output "$run_directory/result.json" &
client_pid=$!
client_status=0
wait "$client_pid" || client_status=$?
client_pid=
exit "$client_status"
