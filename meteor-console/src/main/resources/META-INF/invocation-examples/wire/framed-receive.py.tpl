        size, = struct.unpack("!I", response.read(4))
        body = response.read(size)
        if len(body) != size:
            raise EOFError("incomplete Thrift frame")
        print(body.hex())
