from urllib.request import Request, urlopen

request = Request({{url}}, {{body}}.encode(), {{headers}})
with urlopen(request, timeout=5) as response:
    print(response.read().decode("utf-8"))
