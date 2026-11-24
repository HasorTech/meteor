    var record []byte
    for {
        var marker uint32
        if err := binary.Read(conn, binary.BigEndian, &marker); err != nil { panic(err) }
        fragment := make([]byte, marker & 0x7fffffff)
        if _, err := io.ReadFull(conn, fragment); err != nil { panic(err) }
        record = append(record, fragment...)
        if marker & 0x80000000 != 0 { break }
    }
    fmt.Printf("%x\n", record)
