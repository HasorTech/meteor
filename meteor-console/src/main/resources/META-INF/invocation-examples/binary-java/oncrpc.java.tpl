import java.net.InetAddress;
import java.util.*;
import java.io.IOException;
import org.acplt.oncrpc.*;

final class Request implements XdrAble {
    public void xdrEncode(XdrEncodingStream stream) throws OncRpcException, IOException {
{{write}}
    }

    public void xdrDecode(XdrDecodingStream stream) { throw new UnsupportedOperationException(); }
}

final class Response implements XdrAble {
    Object value;

    public void xdrEncode(XdrEncodingStream stream) { throw new UnsupportedOperationException(); }

    public void xdrDecode(XdrDecodingStream stream) throws OncRpcException, IOException {
        this.value = {{read}};
    }
{{readers}}
}

public final class ExampleMain {
    public static void main(String[] arguments) throws Exception {
        var client = new {{client}}(InetAddress.getByName({{host}}), {{program}}, {{version}}, {{port}});
        try {
            client.setTimeout(5000);
            client.setCharacterEncoding("UTF-8");
            var response = new Response();
            client.call({{procedure}}, new Request(), response);
            System.out.println(response.value);
        } finally {
            client.close();
        }
    }
}
