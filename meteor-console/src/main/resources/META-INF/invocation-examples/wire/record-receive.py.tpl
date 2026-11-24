        while True:
            marker, = struct.unpack("!I", response.read(4))
            size = marker & 0x7fffffff
            body = response.read(size)
            if len(body) != size:
                raise EOFError("incomplete RPC fragment")
            print(body.hex(), end="")
            if marker & 0x80000000:
                print()
                break
