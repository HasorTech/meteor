import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;

public final class ExampleMain {
    public static void main(String[] arguments) throws Throwable {
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName({{application}}) //
                .setUnitName({{unit}}) //
                .setDefaultProtocol({{protocol}});
{{connector}}{{registry}}
        try ({{resourceClose}}MetContext context = configuration.buildContext()) {
{{resources}}            context.start();
{{body}}
        }
    }
}
