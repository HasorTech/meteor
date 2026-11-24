import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
{{javaMetadataImports}}import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ExampleMain {
    public static void main(String[] arguments) throws Exception {
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
        var input = DynamicMessage.newBuilder(types.get("{{requestType}}"));
        JsonFormat.parser().merge(Files.readString(Path.of("request.json")), input);
        var output = DynamicMessage.getDefaultInstance(types.get("{{responseType}}"));
        var method = MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("{{methodPath}}".substring(1))
                .setRequestMarshaller(ProtoUtils.marshaller(input.getDefaultInstanceForType()))
                .setResponseMarshaller(ProtoUtils.marshaller(output)).build();
{{javaMetadata}}        var channel = NettyChannelBuilder.forAddress("{{host}}", {{port}}).usePlaintext(){{javaInterceptor}}.build();
        try {
            var result = ClientCalls.blockingUnaryCall(channel, method,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), input.build());
            System.out.println(JsonFormat.printer().print(result));
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    private static void addType(Map<String, Descriptors.Descriptor> types, Descriptors.Descriptor type) {
        types.put(type.getFullName(), type);
        for (var nested : type.getNestedTypes()) addType(types, nested);
    }

}
