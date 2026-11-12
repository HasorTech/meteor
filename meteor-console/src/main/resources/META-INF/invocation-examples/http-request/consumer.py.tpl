import urllib.request

address = {{url}}
# 当前服务的线上请求。修改业务参数后再次调用。
body = {{body}}.encode("utf-8")
headers = {{headers}}
request = urllib.request.Request(address, data=body, headers=headers, method="POST")
with urllib.request.urlopen(request, timeout=5) as response:
    print(response.read().decode("utf-8"))
