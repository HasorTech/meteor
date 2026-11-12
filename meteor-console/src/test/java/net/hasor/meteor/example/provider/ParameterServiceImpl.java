package net.hasor.meteor.example.provider;
import net.hasor.meteor.example.api.DebugMessage;
import net.hasor.meteor.example.api.ParameterService;

/** 原样返回或计算输入值，方便对照控制台参数与调用结果。 */
public final class ParameterServiceImpl implements ParameterService {
    @Override
    public String ping() {
        return "pong";
    }

    @Override
    public boolean negate(boolean enabled) {
        return !enabled;
    }

    @Override
    public String text(String text) {
        return text;
    }

    @Override
    public String join(String[] values, String separator) {
        return String.join(separator, values);
    }

    @Override
    public DebugMessage merge(DebugMessage first, DebugMessage second) {
        DebugMessage response = new DebugMessage();
        response.setValue(first.getValue() + second.getValue());
        response.setLeft(first.getLeft() + second.getLeft());
        response.setRight(first.getRight() + second.getRight());
        response.setResult(first.getResult() + second.getResult());
        return response;
    }

    @Override
    public int sum(int left, int right) {
        return Math.addExact(left, right);
    }

    @Override
    public long sumLong(long left, long right) {
        return Math.addExact(left, right);
    }

    @Override
    public double scale(double value, double factor) {
        return value * factor;
    }
}
