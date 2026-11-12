package net.hasor.meteor.example.api;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

/**
 * 参数演示：零参数、标量、数组和多个对象，供控制台调试表单使用。
 * gRPC、gRPC-Web 和 Connect 要求单个请求消息，不发布本接口。
 */
@MetService(id = 0x31234573L, name = "ParameterService", group = "console-debug", version = "1.0.0")
public interface ParameterService {
    /** 无参数调用，用于验证空参数表单。 */
    @MetMethod(id = 1)
    String ping();

    /** 布尔参数及返回值。 */
    @MetMethod(id = 2)
    boolean negate(@MetParam(id = 1, name = "enabled") boolean enabled);

    /** 原样返回文本，可输入多行内容；ONC RPC 仅支持 ASCII。 */
    @MetMethod(id = 3)
    String text(@MetParam(id = 1, name = "text") String text);

    /** 数组使用 JSON 输入，分隔符使用文本输入。 */
    @MetMethod(id = 4)
    String join(@MetParam(id = 1, name = "values") String[] values,
                @MetParam(id = 2, name = "separator") String separator);

    /** 两个独立对象参数，各自展示 JSON 编辑器；返回合并后的字段。 */
    @MetMethod(id = 5)
    DebugMessage merge(@MetParam(id = 1, name = "first") DebugMessage first,
                       @MetParam(id = 2, name = "second") DebugMessage second);

    /** 两个单值整数参数。 */
    @MetMethod(id = 6)
    int sum(@MetParam(id = 1, name = "left") int left,
            @MetParam(id = 2, name = "right") int right);

    /** 长整数参数；XML-RPC 数值须保持在有符号 32 位范围内。 */
    @MetMethod(id = 7)
    long sumLong(@MetParam(id = 1, name = "left") long left,
                 @MetParam(id = 2, name = "right") long right);

    /** 小数参数及返回值。 */
    @MetMethod(id = 8)
    double scale(@MetParam(id = 1, name = "value") double value,
                 @MetParam(id = 2, name = "factor") double factor);
}
