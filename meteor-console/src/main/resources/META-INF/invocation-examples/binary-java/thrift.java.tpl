import java.util.*;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.protocol.*;
import org.apache.thrift.transport.*;
import org.apache.thrift.transport.layered.TFramedTransport;

public final class ExampleMain {
    public static void main(String[] arguments) throws Exception {
        try (var transport = {{transport}}) {
            transport.open();
            TProtocol protocol = new {{protocol}}(transport);
            protocol.writeMessageBegin(new TMessage({{method}}, TMessageType.CALL, 1));
            protocol.writeStructBegin(new TStruct("arguments"));
{{write}}
            protocol.writeFieldStop();
            protocol.writeStructEnd();
            protocol.writeMessageEnd();
            transport.flush();
            TMessage reply = protocol.readMessageBegin();
            if (reply.type == TMessageType.EXCEPTION) throw TApplicationException.readFrom(protocol);
            Object result = read(protocol, TType.STRUCT);
            protocol.readMessageEnd();
            System.out.println(result);
        }
    }

    private static Object read(TProtocol protocol, byte kind) throws Exception {
        switch (kind) {
            case TType.BOOL: return protocol.readBool();
            case TType.BYTE: return protocol.readByte();
            case TType.I16: return protocol.readI16();
            case TType.I32: return protocol.readI32();
            case TType.I64: return protocol.readI64();
            case TType.DOUBLE: return protocol.readDouble();
            case TType.STRING: return protocol.readString();
            case TType.UUID: return protocol.readUuid();
            case TType.STRUCT: {
                Map<Short, Object> result = new LinkedHashMap<>();
                protocol.readStructBegin();
                while (true) {
                    TField field = protocol.readFieldBegin();
                    if (field.type == TType.STOP) break;
                    result.put(field.id, read(protocol, field.type));
                    protocol.readFieldEnd();
                }
                protocol.readStructEnd();
                return result;
            }
            case TType.LIST: {
                TList list = protocol.readListBegin();
                List<Object> values = new ArrayList<>();
                for (int i = 0; i < list.size; i++) values.add(read(protocol, list.elemType));
                protocol.readListEnd();
                return values;
            }
            case TType.SET: {
                TSet set = protocol.readSetBegin();
                Set<Object> values = new LinkedHashSet<>();
                for (int i = 0; i < set.size; i++) values.add(read(protocol, set.elemType));
                protocol.readSetEnd();
                return values;
            }
            case TType.MAP: {
                TMap map = protocol.readMapBegin();
                Map<Object, Object> values = new LinkedHashMap<>();
                for (int i = 0; i < map.size; i++) values.put(read(protocol, map.keyType), read(protocol, map.valueType));
                protocol.readMapEnd();
                return values;
            }
            default: throw new IllegalArgumentException("Unknown Thrift type " + kind);
        }
    }
}
