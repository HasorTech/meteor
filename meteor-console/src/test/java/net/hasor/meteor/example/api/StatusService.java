package net.hasor.meteor.example.api;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/** 状态服务，用于观察提供者所在单元及进程。 */
@MetService(id = 0x31234572L, name = "StatusService", group = "console-debug", version = "1.0.0")
public interface StatusService {
    @MetMethod(id = 1)
    DebugMessage status(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 检查服务状态。 */
    @MetMethod(id = 2)
    DebugMessage health(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 返回当前 Java 版本和进程信息。 */
    @MetMethod(id = 3)
    DebugMessage runtime(@MetParam(id = 1, name = "request") DebugMessage request);
}
