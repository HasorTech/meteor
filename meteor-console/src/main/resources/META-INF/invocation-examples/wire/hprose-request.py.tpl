import json
from pathlib import Path


def encode(value):
    if value is None:
        return b"n"
    if isinstance(value, bool):
        return b"t" if value else b"f"
    if isinstance(value, int):
        return f"l{value};".encode()
    if isinstance(value, float):
        return f"d{value};".encode()
    if isinstance(value, str):
        length = len(value.encode("utf-16-le")) // 2
        return f's{length}"'.encode() + value.encode() + b'"'
    if isinstance(value, list):
        return f"a{len(value)}{{".encode() + b"".join(map(encode, value)) + b"}"
    entries = b"".join(encode(key) + encode(item) for key, item in value.items())
    return f"m{len(value)}{{".encode() + entries + b"}"


method = {{method}}
arguments = json.loads(Path("arguments.json").read_text(encoding="utf-8"))
Path("request.bin").write_bytes(b"C" + encode(method) + encode(arguments) + b"z")
