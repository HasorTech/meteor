import base64
import urllib.request

# {{operation}}；以下请求来自当前服务的实际线上契约。
request = urllib.request.Request({{url}}, data=base64.b64decode("{{body}}"),
                                 headers={{headers}}, method="POST")
with urllib.request.urlopen(request, timeout=5) as response:
    print(response.read().hex())
