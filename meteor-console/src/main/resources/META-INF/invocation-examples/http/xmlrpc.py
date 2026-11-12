import base64
import json
import xmlrpc.client

URL = {{url}}
call = json.loads({{call}})

def values(value):
    if isinstance(value, dict):
        if set(value) == {"$binary"}:
            return xmlrpc.client.Binary(base64.b64decode(value["$binary"]))
        return {key: values(item) for key, item in value.items()}
    if isinstance(value, list):
        return [values(item) for item in value]
    return value

with xmlrpc.client.ServerProxy(URL, allow_none=True, use_builtin_types=True) as client:
    print(getattr(client, call["method"])(*values(call["args"])))
