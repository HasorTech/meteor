package main
import (
    "context"
    "encoding/json"
    "fmt"
    "time"
    hessian "github.com/apache/dubbo-go-hessian2"
    "dubbo.apache.org/dubbo-go/v3/client"
    "dubbo.apache.org/dubbo-go/v3/common/constant"
    "dubbo.apache.org/dubbo-go/v3/common/extension"
    _ "dubbo.apache.org/dubbo-go/v3/cluster/cluster/failfast"
    _ "dubbo.apache.org/dubbo-go/v3/cluster/loadbalance/random"
    _ "dubbo.apache.org/dubbo-go/v3/filter/echo"
    _ "dubbo.apache.org/dubbo-go/v3/filter/generic"
    _ "dubbo.apache.org/dubbo-go/v3/filter/graceful_shutdown"
    _ "dubbo.apache.org/dubbo-go/v3/protocol/dubbo"
    _ "dubbo.apache.org/dubbo-go/v3/proxy/proxy_factory"
)
func main() {
    consumer, err := client.NewClient(client.WithClientProtocolDubbo(), client.WithClientURL({{address}}),
        client.WithClientGroup({{group}}), client.WithClientVersion({{version}}),
        client.WithClientSerialization(constant.Hessian2Serialization), client.WithClientRetries(0),
        client.WithClientClusterFailFast(), client.WithClientLoadBalanceRandom(),
        client.WithClientFilter("echo,generic"), client.WithClientRequestTimeout(5*time.Second))
    if err != nil { panic(err) }
    defer extension.GetProtocol("dubbo").Destroy()
    service, err := consumer.NewGenericService({{service}})
    if err != nil { panic(err) }
    ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
    defer cancel()
    values := []hessian.Object{}
    if err := json.Unmarshal([]byte({{jsonArguments}}), &values); err != nil { panic(err) }
    result, err := service.Invoke(ctx, {{method}}, []string{ {{types}} }, values)
    if err != nil { panic(err) }
    fmt.Printf("%v\n", result)
}
