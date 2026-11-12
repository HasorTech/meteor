package net.hasor.meteor.example.consumer;
import net.hasor.core.Inject;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.example.api.CalculatorService;
import net.hasor.meteor.example.api.DebugMessage;
import net.hasor.meteor.example.api.EchoService;
import net.hasor.meteor.example.api.StatusService;

/** 消费者业务类；服务接口由 meteor-hasor 创建代理后交给 Hasor 注入。 */
public class ServiceConsumer {
    @Inject
    private EchoService       echo;
    @Inject
    private CalculatorService calculator;
    @Inject
    private StatusService     status;

    public void call() {
        DebugMessage request = new DebugMessage();
        request.setValue("console-debug");
        request.setLeft(20);
        request.setRight(22);
        request.setResult(7);

        DebugMessage echoed = this.echo.echo(request);
        if (echoed == null) {
            throw new IllegalStateException("EchoService returned null");
        }
        if (echoed.getValue() == null || !echoed.getValue().endsWith(":console-debug")) {
            throw new IllegalStateException("Unexpected echo result: " + echoed.getValue());
        }
        if (echoed.getLeft() != 20 || echoed.getRight() != 22 || echoed.getResult() != 7) {
            throw new IllegalStateException("Response did not preserve request fields: EchoService.echo");
        }
        System.out.println("[CALL] EchoService.echo -> " + echoed.getValue());

        DebugMessage calculated = this.calculator.add(request);
        if (calculated == null) {
            throw new IllegalStateException("CalculatorService returned null");
        }
        if (calculated.getResult() != 42) {
            throw new IllegalStateException("Unexpected calculator result: " + calculated.getResult());
        }
        if (calculated.getLeft() != 20 || calculated.getRight() != 22
                || !StringUtils.equals("console-debug", calculated.getValue())) {
            throw new IllegalStateException("Calculator response did not preserve request fields");
        }
        System.out.println("[CALL] CalculatorService.add -> " + calculated.getResult());

        DebugMessage currentStatus = this.status.status(request);
        if (currentStatus == null) {
            throw new IllegalStateException("StatusService returned null");
        }
        if (currentStatus.getValue() == null || !currentStatus.getValue().startsWith("status:")
                || !currentStatus.getValue().endsWith(":console-debug")) {
            throw new IllegalStateException("Unexpected status result: " + currentStatus.getValue());
        }
        if (currentStatus.getLeft() != 20 || currentStatus.getRight() != 22 || currentStatus.getResult() != 7) {
            throw new IllegalStateException("Response did not preserve request fields: StatusService.status");
        }
        System.out.println("[CALL] StatusService.status -> " + currentStatus.getValue());
    }
}
