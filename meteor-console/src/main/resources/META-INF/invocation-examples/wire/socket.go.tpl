package main

import (
    "encoding/binary"
    "fmt"
    "io"
    "net"
    "os"
    "time"
)

func main() {
    body, err := os.ReadFile("request.bin")
    if err != nil { panic(err) }
    conn, err := net.DialTimeout("tcp", net.JoinHostPort({{host}}, "{{port}}"), 5*time.Second)
    if err != nil { panic(err) }
    defer conn.Close()
    if err := conn.SetDeadline(time.Now().Add(5*time.Second)); err != nil { panic(err) }
    if _, err := conn.Write(body); err != nil { panic(err) }
{{receive}}
}
