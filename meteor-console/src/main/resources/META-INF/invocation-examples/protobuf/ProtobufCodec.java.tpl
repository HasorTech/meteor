import com.connectrpc.Codec;
import com.connectrpc.ErrorDetailParser;
import com.connectrpc.SerializationStrategy;
import com.connectrpc.extensions.GoogleJavaProtobufStrategy;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import kotlin.reflect.KClass;
import okio.Buffer;
import okio.BufferedSource;

final class ProtobufCodec implements SerializationStrategy, Codec<DynamicMessage> {
    private final Descriptors.Descriptor requestType;
    private final Descriptors.Descriptor responseType;

    private ProtobufCodec(Descriptors.Descriptor requestType, Descriptors.Descriptor responseType) {
        this.requestType = requestType;
        this.responseType = responseType;
    }

    static ProtobufCodec load() throws Exception {
        var files = new HashMap<String, Descriptors.FileDescriptor>();
        var types = new HashMap<String, Descriptors.Descriptor>();
        {{javaDescriptors}}
        for (var file : definition.getFileList()) {
            var dependencies = file.getDependencyList().stream().map(files::get)
                    .toArray(Descriptors.FileDescriptor[]::new);
            var descriptor = Descriptors.FileDescriptor.buildFrom(file, dependencies);
            files.put(file.getName(), descriptor);
            for (var type : descriptor.getMessageTypes()) addType(types, type);
        }
        return new ProtobufCodec(types.get("{{requestType}}"), types.get("{{responseType}}"));
    }

    DynamicMessage request(String json) throws Exception {
        var request = DynamicMessage.newBuilder(requestType);
        JsonFormat.parser().merge(json, request);
        return request.build();
    }

    private static void addType(Map<String, Descriptors.Descriptor> types, Descriptors.Descriptor type) {
        types.put(type.getFullName(), type);
        for (var nested : type.getNestedTypes()) addType(types, nested);
    }

    public String serializationName() { return "proto"; }
    public String encodingName() { return "proto"; }
    public ErrorDetailParser errorDetailParser() { return new GoogleJavaProtobufStrategy().errorDetailParser(); }

    @SuppressWarnings("unchecked")
    public <E> Codec<E> codec(KClass<E> type) { return (Codec<E>) this; }

    public Buffer serialize(DynamicMessage value) { return new Buffer().write(value.toByteArray()); }
    public Buffer deterministicSerialize(DynamicMessage value) { return serialize(value); }

    public DynamicMessage deserialize(BufferedSource source) {
        try { return DynamicMessage.parseFrom(responseType, source.readByteArray()); }
        catch (Exception failure) { throw new IllegalArgumentException(failure); }
    }
}
