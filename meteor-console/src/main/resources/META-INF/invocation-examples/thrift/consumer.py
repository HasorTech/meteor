#!/usr/bin/env python3
"""调用控制台当前选中的真实 Thrift 端点；契约由 service.thrift 生成。"""
import uuid
from urllib.parse import unquote, urlsplit
from thrift.protocol import TBinaryProtocol, TMultiplexedProtocol
from thrift.transport import TSocket, TTransport, THttpClient
from meteor_example import {{service}}, ttypes

address = urlsplit({{address}})
connection = {{connection}}
connection.setTimeout(10000)
transport = TTransport.{{transport}}(connection)
client = {{service}}.Client({{protocol}})

transport.open()
try:
    # 按实际业务调整参数；方法名及参数形状均来自此端点的线上契约。
    print(getattr(client, {{method}})({{arguments}}))
finally:
    transport.close()
