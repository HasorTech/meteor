from pathlib import Path
import socket
import struct

with socket.create_connection(({{host}}, {{port}}), timeout=5) as connection:
    connection.sendall(Path("request.bin").read_bytes())
    with connection.makefile("rb") as response:
{{receive}}
