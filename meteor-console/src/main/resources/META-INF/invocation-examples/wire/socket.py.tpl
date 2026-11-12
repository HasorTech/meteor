import socket
import struct
import base64

# {{operation}}；请求来自当前服务的实际线上契约，响应为十六进制报文。
request = base64.b64decode("{{body}}")
def exact(connection, length):
    result = b""
    while len(result) < length:
        data = connection.recv(length - len(result))
        if not data: raise EOFError("incomplete RPC response")
        result += data
    return result
with socket.socket(socket.AF_INET, socket.{{socketKind}}) as connection:
    connection.settimeout(5)
    connection.connect(({{host}}, {{port}}))
    connection.sendall(request)
    if {{udp}}:
        result = connection.recv(65535)
    else:
        result = b""
        while True:
            marker = struct.unpack('!I', exact(connection, 4))[0]
            result += exact(connection, marker & 0x7fffffff)
            if {{thrift}} or marker & 0x80000000: break
    print(result.hex())
