package net.hasor.meteor.performance;
import java.io.Serializable;
import net.hasor.meteor.annotation.MetField;
import net.hasor.meteor.annotation.MetType;

/** 相同的单字段对象用于所有协议，保持负载与类型映射口径一致。 */
@MetType(name = "EchoPayload")
public final class EchoPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    @MetField(id = 1, name = "data")
    private byte[] data;

    public EchoPayload() {
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
