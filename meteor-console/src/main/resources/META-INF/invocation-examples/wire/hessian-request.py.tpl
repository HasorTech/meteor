import json
from pathlib import Path
from struct import iter_unpack, pack


def encode(value):
    if value is None:
        return b"N"
    if isinstance(value, bool):
        return b"T" if value else b"F"
    if isinstance(value, int):
        if -(2**31) <= value < 2**31:
            return b"I" + pack(">i", value)
        return b"L" + pack(">q", value)
    if isinstance(value, float):
        return b"D" + pack(">d", value)
    if isinstance(value, str):
        # Hessian counts UTF-16 code units, including surrogate pairs.
        utf16 = value.encode("utf-16-be")
        chars = [chr(unit[0]).encode("utf-8", "surrogatepass") for unit in iter_unpack(">H", utf16)]
        chunks = [chars[index:index + 32768] for index in range(0, len(chars), 32768)] or [[]]
        return b"".join(
            (b"R" if index < len(chunks) - 1 else b"S") + pack(">H", len(chunk)) + b"".join(chunk)
            for index, chunk in enumerate(chunks)
        )
    if isinstance(value, list):
        return b"X" + encode(len(value)) + b"".join(map(encode, value))
    entries = b"".join(encode(key) + encode(item) for key, item in value.items())
    return b"H" + entries + b"Z"


method = {{method}}
arguments = json.loads(Path("arguments.json").read_text(encoding="utf-8"))
Path("request.bin").write_bytes(b"H\x02\x00C" + encode(method) + encode(len(arguments)) + b"".join(map(encode, arguments)))
