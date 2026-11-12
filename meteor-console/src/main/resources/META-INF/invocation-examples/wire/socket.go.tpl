package main
import (
    "encoding/base64"
    "encoding/binary"
    "encoding/hex"
    "fmt"
    "io"
    "net"
    "time"
)
func main() {
    // {{operation}}；请求来自当前服务的实际线上契约，响应为十六进制报文。
    body, err := base64.StdEncoding.DecodeString("{{body}}")
    if err != nil { panic(err) }
    connection, err := net.DialTimeout("{{network}}", net.JoinHostPort({{host}}, "{{port}}"), 5 * time.Second)
    if err != nil { panic(err) }
    defer connection.Close()
    if err := connection.SetDeadline(time.Now().Add(5 * time.Second)); err != nil { panic(err) }
    for len(body) > 0 {
        written, err := connection.Write(body)
        if err != nil { panic(err) }
        body = body[written:]
    }
    result := []byte{}
    if {{udp}} {
        result = make([]byte, 65535)
        size, err := connection.Read(result)
        if err != nil { panic(err) }
        result = result[:size]
    } else {
        for {
            header := make([]byte, 4)
            if _, err := io.ReadFull(connection, header); err != nil { panic(err) }
            marker := binary.BigEndian.Uint32(header)
            part := make([]byte, marker & 0x7fffffff)
            if _, err := io.ReadFull(connection, part); err != nil { panic(err) }
            result = append(result, part...)
            if {{thrift}} || marker & 0x80000000 != 0 { break }
        }
    }
    fmt.Println(hex.EncodeToString(result))
}
