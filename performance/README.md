# Meteor RPC 压测

用两个 Java 进程测 RPC 调用速度。服务端原样返回收到的数据，客户端逐字节检查。支持 15 种协议，每轮测一种。

## 怎么跑

需要 JDK 17 和 Bash。在仓库根目录用 `local` 启动压测。

```bash
./performance/run.sh local
./performance/run.sh local --protocol grpc --concurrency 32 --payload 4096
```

`local` 在本机启动服务端和客户端，跑完后关掉服务端。默认用 `127.0.0.1`，也可以加 `--host ::1`。

用两台机器压测时，先在服务端启动监听，再到客户端运行第二条命令。把地址换成服务端的实际地址。

```bash
./performance/run.sh server --protocol rsf --host 0.0.0.0 --port 19000
./performance/run.sh client --protocol rsf --address rsf://192.168.1.10:19000
```

## 常用参数

| 参数 | 默认值 | 用途 |
| --- | --- | --- |
| `--protocol` | `rsf` | 选择协议 |
| `--concurrency` | 16 | 客户端线程数，每个线程等上一次请求返回后再发下一次 |
| `--payload` | 1024 | 要原样返回的数据大小，单位是字节 |
| `--warmup` | 5 | 预热时间，单位是秒 |
| `--duration` | 30 | 正式测量时间，单位是秒 |
| `--timeout` | 3000 | 一次调用最多等多久，单位是毫秒 |
| `--client-pool-size` | 1 | TCP 或 HTTP 连到同一地址时，最多用几条连接 |
| `--maximum-requests` | 框架默认值 | 客户端同时等待结果的请求上限 |

默认每个 Java 进程能看到 4 个 CPU 线程。可用 `JAVA_OPTS='-XX:ActiveProcessorCount=32'` 调整。运行 `./performance/run.sh help` 查看其他选项。

## 支持的协议

- TCP 支持 `rsf`、`dubbo`、`thrift` 和 `oncrpc`。
- UDP 支持 `oncrpc-udp`。
- `grpc` 和 `triple` 使用 HTTP/2。
- `grpc-web`、`connect`、`hessian`、`hprose`、`jsonrpc`、`xmlrpc`、`soap` 和 `thrift-http` 默认使用 HTTP/1.1，可用 `--http-version 2` 切换。

## 怎么看结果

本机报告在 `performance/build/runs/run-*/result.json`。分机时，到客户端的运行目录找 `client/result.json`。

打开报告里的 `measurement` 看正式结果。

- `successfulRequestsPerSecond` 是每秒成功的请求数。
- `successLatencyMicros.p99` 表示 99% 的成功请求在多少微秒内返回。
- `failures` 和 `timeouts` 是失败和超时次数。`errorSamples` 里有部分错误信息。

## 构建和测试

```bash
./gradlew -PincludeTestProjects=false -PincludePerformance=true :performance:test :performance:installDist
```

要用旁边 `neta` 的源码，在启动命令前加 `METEOR_NETA_SOURCE="$PWD/../neta"`。
