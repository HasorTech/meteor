package net.hasor.meteor.example.provider;
import java.util.Locale;
import net.hasor.core.InjectSettings;
import net.hasor.meteor.example.api.DebugMessage;
import net.hasor.meteor.example.api.EchoService;

/** 在响应中返回提供者单元和 PID，便于观察服务路由。 */
public final class EchoServiceImpl implements EchoService {
    @InjectSettings(value = "meteor.unit", defaultValue = "default")
    private String unit;

    @Override
    public DebugMessage echo(DebugMessage request) {
        DebugMessage response = request.copy();
        response.setValue(this.unit + ":" + ProcessHandle.current().pid() + ":" + request.getValue());
        return response;
    }
    @Override
    public DebugMessage uppercase(DebugMessage request) {
        DebugMessage response = request.copy();
        response.setValue(request.getValue().toUpperCase(Locale.ROOT));
        return response;
    }

    @Override
    public DebugMessage repeat(DebugMessage request) {
        int count = request.getLeft();
        if (count < 0 || count > 20) {
            throw new IllegalArgumentException("重复次数必须在 0～20 之间");
        }
        DebugMessage response = request.copy();
        response.setValue(request.getValue().repeat(count));
        return response;
    }
}
