set -euo pipefail
curl{{shellHttpVersion}} --fail-with-body --max-time 5 \
{{shellHeaders}}
  --data-binary @request.bin {{shellUrl}} | od -An -v -tx1
