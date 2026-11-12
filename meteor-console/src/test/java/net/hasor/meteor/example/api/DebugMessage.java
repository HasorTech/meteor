package net.hasor.meteor.example.api;
import java.io.Serializable;
import net.hasor.meteor.annotation.MetField;

/** 请求和响应对象，字段号同时用于 Protobuf、Thrift 和 XDR。 */
public class DebugMessage implements Serializable {
    private static final long   serialVersionUID = 1L;
    @MetField(id = 1)
    private              String value            = "";
    @MetField(id = 2)
    private              int    left;
    @MetField(id = 3)
    private              int    right;
    @MetField(id = 4)
    private              int    result;

    /** 复制请求字段，供示例服务在独立响应中修改业务结果。 */
    public DebugMessage copy() {
        DebugMessage message = new DebugMessage();
        message.setValue(this.value);
        message.setLeft(this.left);
        message.setRight(this.right);
        message.setResult(this.result);
        return message;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getLeft() {
        return this.left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return this.right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public int getResult() {
        return this.result;
    }

    public void setResult(int result) {
        this.result = result;
    }
}
