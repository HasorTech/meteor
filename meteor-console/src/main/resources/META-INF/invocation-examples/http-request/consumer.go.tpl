package main

import (
    "fmt"
    "io"
    "net/http"
    "strings"
    "time"
)

func main() {
    request, err := http.NewRequest("POST", {{url}}, strings.NewReader({{body}}))
    if err != nil { panic(err) }
    headers := map[string]string{{headers}}
    for key, value := range headers { request.Header.Set(key, value) }
    client := &http.Client{Timeout: 5 * time.Second}
    response, err := client.Do(request)
    if err != nil { panic(err) }
    defer response.Body.Close()
    result, err := io.ReadAll(response.Body)
    if err != nil { panic(err) }
    if response.StatusCode < 200 || response.StatusCode >= 300 { panic(string(result)) }
    fmt.Println(string(result))
}
