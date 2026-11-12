
## 使用注册中心

上面的直连示例由调用方指定服务地址；改用注册中心时，服务 ID、接口和调用方法保持不变，只调整依赖、连接配置和代理获取方式。

依赖：{{dependencies}}。

{{description}}

在 `buildContext()` 前，用以下配置替换原来的连接器配置：

{{configuration}}

保留服务声明与 `binding`。等待注册中心发现可用地址后，将 `context.getRsfClient(new InterAddress(...)).getRemote(binding)` 改为 `context.getRsfClient().getRemote(binding)`，即可按服务 ID 选择提供者。{{resourceClose}}
