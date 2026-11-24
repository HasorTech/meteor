    var size uint32
    if err := binary.Read(conn, binary.BigEndian, &size); err != nil { panic(err) }
    response := make([]byte, size)
    if _, err := io.ReadFull(conn, response); err != nil { panic(err) }
    fmt.Printf("%x\n", response)
