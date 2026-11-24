import json
from urllib.request import Request, urlopen

payload = {"jsonrpc": "2.0", "id": 1, "method": {{method}}, "params": {{arguments}}}
request = Request({{url}}, json.dumps(payload).encode(), {"Content-Type": "application/json"})
with urlopen(request, timeout=15) as response:
    reply = json.load(response)
if "error" in reply:
    raise RuntimeError(reply["error"])
print(json.dumps(reply["result"], ensure_ascii=False))
