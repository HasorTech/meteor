import net.hasor.meteor.bootstrap.MetConfiguration;
{{registryImport}}
public class ExampleMain {
    public static void main(String[] args) throws Throwable {
{{configuration}}
{{connector}}{{registry}}        try (var context = configuration.buildContext()) {
{{resources}}{{body}}        }
    }
}
