package main
import (
    "bytes"
    "encoding/base64"
    "encoding/hex"
    "fmt"
    "io"
    "net/http"
    "time"
)
func main() {
    // {{operation}}；请求来自当前服务的实际线上契约。
    body, err := base64.StdEncoding.DecodeString("{{body}}")
    if err != nil { panic(err) }
    request, err := http.NewRequest("POST", {{url}}, bytes.NewReader(body))
    if err != nil { panic(err) }
    headers := map[string]string{{headers}}
    for name, value := range headers { request.Header.Set(name, value) }
    client := &http.Client{Timeout: 5 * time.Second}
    response, err := client.Do(request)
    if err != nil { panic(err) }
    defer response.Body.Close()
    result, err := io.ReadAll(response.Body)
    if err != nil { panic(err) }
    if response.StatusCode < 200 || response.StatusCode >= 300 { panic(string(result)) }
    fmt.Println(hex.EncodeToString(result))
}
