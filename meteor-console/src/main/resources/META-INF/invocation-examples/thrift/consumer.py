{{imports}}

{{connection}}
connection.setTimeout(10000)
transport = TTransport.{{transport}}(connection)
client = {{service}}.Client({{protocol}})

transport.open()
try:
    print(getattr(client, {{method}})({{arguments}}))
finally:
    transport.close()
