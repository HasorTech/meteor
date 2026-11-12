import com.connectrpc.*;
import com.connectrpc.impl.ProtocolClient;
import com.connectrpc.extensions.GoogleJavaProtobufStrategy;
import com.connectrpc.okhttp.ConnectOkHttpClient;
import com.connectrpc.protocols.NetworkProtocol;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import java.util.*;
import java.util.concurrent.*;
import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import okhttp3.OkHttpClient;
import okio.Buffer;
import okio.BufferedSource;

public final class ExampleMain {
    public static void main(String[] arguments) throws Throwable {
        var files = new HashMap<String, Descriptors.FileDescriptor>();
        var types = new HashMap<String, Descriptors.Descriptor>();
        var definition = FileDescriptorSet.parseFrom(Base64.getDecoder().decode("{{descriptors}}"));
        for (var file : definition.getFileList()) {
            var dependencies = file.getDependencyList().stream().map(files::get)
                    .toArray(Descriptors.FileDescriptor[]::new);
            var descriptor = Descriptors.FileDescriptor.buildFrom(file, dependencies);
            files.put(file.getName(), descriptor);
            for (var type : descriptor.getMessageTypes()) types.put(type.getFullName(), type);
        }
        var input = DynamicMessage.newBuilder(types.get("{{requestType}}"));
        JsonFormat.parser().merge({{requestJson}}, input);
        var responseType = types.get("{{responseType}}");
        // 动态消息使用 SDK 的公开序列化策略，Connect 的封装、网络和错误仍由 SDK 处理。
        var strategy = new SerializationStrategy() {
            public String serializationName() { return "proto"; }
            public ErrorDetailParser errorDetailParser() {
                return new GoogleJavaProtobufStrategy().errorDetailParser();
            }
            @SuppressWarnings("unchecked")
            public <E> Codec<E> codec(KClass<E> type) {
                return (Codec<E>) new Codec<DynamicMessage>() {
                    public String encodingName() { return "proto"; }
                    public Buffer serialize(DynamicMessage value) { return new Buffer().write(value.toByteArray()); }
                    public Buffer deterministicSerialize(DynamicMessage value) { return serialize(value); }
                    public DynamicMessage deserialize(BufferedSource source) {
                        try { return DynamicMessage.parseFrom(responseType, source.readByteArray()); }
                        catch (Exception failure) { throw new IllegalArgumentException(failure); }
                    }
                };
            }
        };
        var http = new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build();
        try {
            var config = new ProtocolClientConfig({{baseUrl}}, strategy, NetworkProtocol.{{network}});
            var client = new ProtocolClient(new ConnectOkHttpClient(http), config);
            var type = JvmClassMappingKt.getKotlinClass(DynamicMessage.class);
            var method = new MethodSpec<DynamicMessage, DynamicMessage>("{{methodName}}", type, type,
                    StreamType.UNARY, Idempotency.UNKNOWN);
            var result = new CompletableFuture<ResponseMessage<DynamicMessage>>();
            var cancel = client.unary(input.build(), Collections.emptyMap(), method, response -> {
                result.complete(response);
                return Unit.INSTANCE;
            });
            try {
                var response = result.get(5, TimeUnit.SECONDS);
                if (response instanceof ResponseMessage.Failure<DynamicMessage> failure) throw failure.getCause();
                System.out.println(JsonFormat.printer().print(((ResponseMessage.Success<DynamicMessage>) response).getMessage()));
            } catch (TimeoutException | InterruptedException failure) {
                cancel.invoke();
                throw failure;
            }
        } finally {
            http.dispatcher().executorService().shutdownNow();
            http.connectionPool().evictAll();
        }
    }
}
