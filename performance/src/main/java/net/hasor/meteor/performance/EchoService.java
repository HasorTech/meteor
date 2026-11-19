package net.hasor.meteor.performance;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/** 压测自有契约，不复用测试矩阵或示例工程。 */
@MetService(id = 88001, group = "performance", name = "EchoService", version = "1")
public interface EchoService {
    @MetMethod(id = 1)
    EchoPayload echo(@MetParam(id = 1, name = "payload") EchoPayload payload);
}
