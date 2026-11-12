import java.net.InetAddress;
import java.util.*;
import java.io.IOException;
import org.acplt.oncrpc.*;

public final class ExampleMain {
    public static void main(String[] arguments) throws Exception {
        var client = new {{client}}(InetAddress.getByName({{host}}), {{program}}, {{version}}, {{port}});
        try {
            client.setTimeout(5000);
            client.setCharacterEncoding("UTF-8");
            Object[] result = new Object[1];
            XdrAble request = new XdrAble() {
                public void xdrEncode(XdrEncodingStream stream) throws OncRpcException, IOException {
{{write}}
                }
                public void xdrDecode(XdrDecodingStream stream) { throw new UnsupportedOperationException(); }
            };
            XdrAble response = new XdrAble() {
                public void xdrEncode(XdrEncodingStream stream) { throw new UnsupportedOperationException(); }
                public void xdrDecode(XdrDecodingStream stream) throws OncRpcException, IOException {
                    result[0] = {{read}};
                }
            };
            client.call({{procedure}}, request, response);
            System.out.println(result[0]);
        } finally {
            client.close();
        }
    }
{{readers}}
}
