set -euo pipefail
# {{operation}}；请求来自当前服务的实际线上契约；响应按十六进制输出。
printf '%s' '{{body}}' | base64 --decode | curl --http2-prior-knowledge --fail-with-body --max-time 5 \
{{shellHeaders}}
  --data-binary @- {{shellUrl}} | od -An -v -tx1
