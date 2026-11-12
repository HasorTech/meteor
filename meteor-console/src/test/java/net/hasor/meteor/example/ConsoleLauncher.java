package net.hasor.meteor.example;
import java.nio.file.Path;
import net.hasor.meteor.console.ConsoleMain;

/** 使用测试配置桥接正式控制台主入口，继承正式装配逻辑。 */
public class ConsoleLauncher extends ConsoleMain {
    public static void main(String[] arguments) throws Exception {
        String[] configured = new String[arguments.length + 1];
        configured[0] = "--config=" + Path.of(ConsoleLauncher.class.getResource("/meteor.properties").toURI());
        System.arraycopy(arguments, 0, configured, 1, arguments.length);
        ConsoleMain.main(configured);
    }
}
