from pathlib import Path
import urllib.request

request = urllib.request.Request({{url}}, Path("request.bin").read_bytes(), {{headers}})
with urllib.request.urlopen(request, timeout=5) as response:
    print(response.read().hex())
