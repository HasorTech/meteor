package net.hasor.meteor.example.api;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/** 显示实际响应的提供者，便于观察路由和实例上下线。 */
@MetService(id = 0x31234570L, name = "EchoService", group = "console-debug", version = "1.0.0")
public interface EchoService {
    @MetMethod(id = 1)
    DebugMessage echo(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 将文本转换为大写。 */
    @MetMethod(id = 2)
    DebugMessage uppercase(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 按 left 指定的次数重复文本，范围 0～20。 */
    @MetMethod(id = 3)
    DebugMessage repeat(@MetParam(id = 1, name = "request") DebugMessage request);
}
