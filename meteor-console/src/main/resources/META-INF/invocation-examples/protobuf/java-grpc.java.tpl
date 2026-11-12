import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public final class ExampleMain {
    public static void main(String[] arguments) throws Exception {
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
        var output = DynamicMessage.getDefaultInstance(types.get("{{responseType}}"));
        var method = MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("{{methodPath}}".substring(1))
                .setRequestMarshaller(ProtoUtils.marshaller(input.getDefaultInstanceForType()))
                .setResponseMarshaller(ProtoUtils.marshaller(output)).build();
        var headers = new Metadata();
        headers.put(Metadata.Key.of("tri-service-group", Metadata.ASCII_STRING_MARSHALLER), {{group}});
        headers.put(Metadata.Key.of("tri-service-version", Metadata.ASCII_STRING_MARSHALLER), {{version}});
        var channel = NettyChannelBuilder.forAddress("{{host}}", {{port}}).usePlaintext()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(headers)).build();
        try {
            var result = ClientCalls.blockingUnaryCall(channel, method,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), input.build());
            System.out.println(JsonFormat.printer().print(result));
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
