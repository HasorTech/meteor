from xmlrpc.client import ServerProxy

with ServerProxy({{url}}, allow_none=True, use_builtin_types=True) as client:
    print(getattr(client, {{method}})(*{{arguments}}))
