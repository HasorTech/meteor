import com.connectrpc.Idempotency;
import com.connectrpc.MethodSpec;
import com.connectrpc.ProtocolClientConfig;
import com.connectrpc.ResponseMessageKt;
import com.connectrpc.StreamType;
import com.connectrpc.impl.ProtocolClient;
import com.connectrpc.okhttp.ConnectOkHttpClient;
import com.connectrpc.protocols.NetworkProtocol;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import kotlin.jvm.JvmClassMappingKt;
import okhttp3.OkHttpClient;

public final class ExampleMain {
    public static void main(String[] args) throws Exception {
        var codec = ProtobufCodec.load();
        var request = codec.request(Files.readString(Path.of("request.json")));
        var http = new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build();
        try {
            var client = new ProtocolClient(new ConnectOkHttpClient(http),
                    new ProtocolClientConfig({{baseUrl}}, codec, NetworkProtocol.{{network}}));
            var type = JvmClassMappingKt.getKotlinClass(DynamicMessage.class);
            var method = new MethodSpec<>("{{methodName}}", type, type, StreamType.UNARY, Idempotency.UNKNOWN);
            var response = client.unaryBlocking(request, Map.of(), method).execute();
            System.out.println(JsonFormat.printer().print(ResponseMessageKt.getOrThrow(response)));
        } finally {
            http.dispatcher().executorService().shutdownNow();
            http.connectionPool().evictAll();
        }
    }
}
