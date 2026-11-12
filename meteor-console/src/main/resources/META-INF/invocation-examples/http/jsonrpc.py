import json
import urllib.request

URL = {{url}}
call = json.loads({{call}})
payload = {"jsonrpc": "2.0", "id": 1, "method": call["method"], "params": call["args"]}
request = urllib.request.Request(URL, json.dumps(payload).encode(), {"Content-Type": "application/json"})
with urllib.request.urlopen(request, timeout=15) as response:
    result = json.load(response)
if "error" in result:
    raise RuntimeError(result["error"])
print(json.dumps(result.get("result"), ensure_ascii=False, indent=2))
