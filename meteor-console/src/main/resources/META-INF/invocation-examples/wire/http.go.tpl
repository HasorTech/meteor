package main
import (
    "bytes"
    "encoding/hex"
    "fmt"
    "io"
    "net/http"
    "os"
    "time"
)
func main() {
    body, err := os.ReadFile("request.bin")
    if err != nil { panic(err) }
    request, err := http.NewRequest("POST", {{url}}, bytes.NewReader(body))
    if err != nil { panic(err) }
    for name, value := range map[string]string{{headers}} { request.Header.Set(name, value) }
    response, err := (&http.Client{Timeout: 5 * time.Second}).Do(request)
    if err != nil { panic(err) }
    defer response.Body.Close()
    result, err := io.ReadAll(response.Body)
    if err != nil { panic(err) }
    if response.StatusCode < 200 || response.StatusCode >= 300 { panic(string(result)) }
    fmt.Println(hex.EncodeToString(result))
}
