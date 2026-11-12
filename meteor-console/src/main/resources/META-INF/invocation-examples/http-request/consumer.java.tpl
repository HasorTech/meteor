import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

public final class ExampleMain {
    public static void main(String[] arguments) throws Exception {
        Service service = Service.create(new QName("urn:meteor:debug", "DebugService"));
        QName port = new QName("urn:meteor:debug", "DebugPort");
        service.addPort(port, {{soapBinding}}, {{url}});
        var client = service.createDispatch(port, SOAPMessage.class, Service.Mode.MESSAGE);
        client.getRequestContext().put("com.sun.xml.ws.connect.timeout", 5000);
        client.getRequestContext().put("com.sun.xml.ws.request.timeout", 5000);
        client.getRequestContext().put("javax.xml.ws.soap.http.soapaction.use", true);
        client.getRequestContext().put("javax.xml.ws.soap.http.soapaction.uri", {{soapAction}});
        byte[] xml = {{body}}.getBytes(StandardCharsets.UTF_8);
        SOAPMessage request = MessageFactory.newInstance({{soapVersion}})
                .createMessage(null, new ByteArrayInputStream(xml));
        client.invoke(request).writeTo(System.out);
    }
}
