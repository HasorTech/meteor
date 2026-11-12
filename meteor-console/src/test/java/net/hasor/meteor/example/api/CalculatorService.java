package net.hasor.meteor.example.api;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/** 加法服务，用于观察同一应用中不同服务的协议配置。 */
@MetService(id = 0x31234571L, name = "CalculatorService", group = "console-debug", version = "1.0.0")
public interface CalculatorService {
    @MetMethod(id = 1)
    DebugMessage add(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 计算 left 减 right。 */
    @MetMethod(id = 2)
    DebugMessage subtract(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 计算 left 乘 right。 */
    @MetMethod(id = 3)
    DebugMessage multiply(@MetParam(id = 1, name = "request") DebugMessage request);

    /** 整数除法；除数为零时返回异常，便于验证错误展示。 */
    @MetMethod(id = 4)
    DebugMessage divide(@MetParam(id = 1, name = "request") DebugMessage request);
}
