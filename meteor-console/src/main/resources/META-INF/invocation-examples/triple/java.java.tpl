import java.io.*;
import java.util.concurrent.TimeUnit;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;

public final class ExampleMain {
    public static void main(String[] arguments) throws Throwable {
        Object[] values = { {{arguments}} };
        String[] types = { {{types}} };
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        CodedOutputStream wrapper = CodedOutputStream.newInstance(buffer);
        wrapper.writeString(1, "hessian4");
        for (Object value : values) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Hessian2Output output = new Hessian2Output(bytes);
            output.writeObject(value);
            output.flush();
            wrapper.writeByteArray(2, bytes.toByteArray());
        }
        for (String type : types) wrapper.writeString(3, type);
        wrapper.flush();
        MethodDescriptor.Marshaller<byte[]> bytes = new MethodDescriptor.Marshaller<>() {
            public InputStream stream(byte[] value) { return new ByteArrayInputStream(value); }
            public byte[] parse(InputStream input) {
                try { return input.readAllBytes(); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
        };
        var method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY).setFullMethodName({{method}})
                .setRequestMarshaller(bytes).setResponseMarshaller(bytes).build();
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("tri-service-group", Metadata.ASCII_STRING_MARSHALLER), {{group}});
        headers.put(Metadata.Key.of("tri-service-version", Metadata.ASCII_STRING_MARSHALLER), {{version}});
        var channel = NettyChannelBuilder.forAddress({{host}}, {{port}}).usePlaintext()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(headers)).build();
        try {
            byte[] response = ClientCalls.blockingUnaryCall(channel, method,
                    CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS), buffer.toByteArray());
            CodedInputStream input = CodedInputStream.newInstance(response);
            byte[] value = null;
            for (int tag; (tag = input.readTag()) != 0;) {
                if (tag == 18) value = input.readByteArray();
                else input.skipField(tag);
            }
            if (value == null) throw new IOException("Triple response has no result");
            Hessian2Input result = new Hessian2Input(new ByteArrayInputStream(value));
            System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result.readObject()));
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
