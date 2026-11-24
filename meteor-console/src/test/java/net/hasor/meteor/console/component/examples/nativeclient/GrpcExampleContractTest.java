/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.console.component.examples.nativeclient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;
import net.hasor.realmeteor.console.ProtoExampleCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GrpcExampleContractTest {
    @TempDir
    Path directory;

    @Test
    void rendersOnlyTheSelectedMethodAndItsReachableTypes() throws Exception {
        // 构造包含跨文件类型、嵌套消息、枚举及无关方法的真实 Protobuf 契约。
        Path original = Files.createDirectory(this.directory.resolve("original"));
        Files.writeString(original.resolve("payload.proto"), """
                syntax = "proto3";
                package samples.data;
                
                message Envelope {
                  message Item {
                    optional int32 count = 1;
                  }
                  enum State {
                    UNKNOWN = 0;
                    READY = 1;
                  }
                  oneof choice {
                    string text = 2 [json_name = "customText"];
                    Item item = 3;
                  }
                  map<string, State> states = 4;
                  message UnusedChild {}
                }
                message Unused {}
                """);
        Path entry = original.resolve("orders.proto");
        Files.writeString(entry, """
                syntax = "proto3";
                package samples;
                import "payload.proto";
                service Orders {
                  rpc Put(samples.data.Envelope) returns (samples.data.Envelope.Item);
                  rpc Unused(samples.data.Unused) returns (samples.data.Unused);
                }
                """);
        var source = GrpcProtoSchema.fromDescriptorSet(Files.readAllBytes(ProtoExampleCompiler.compile(entry)));

        // 把实际展示的围栏保存为 .proto 文件，再按公开入口重新解析。
        var originalMethod = source.method("samples.Orders", "Put");
        String document = new GrpcExampleContract(originalMethod.getService(), List.of(originalMethod)).markdown();
        Path rendered = Files.createDirectory(this.directory.resolve("rendered"));
        var files = Pattern.compile("(?ms)^```protobuf ([\\w.-]+)\\R(.*?)^```").matcher(document);
        while (files.find()) {
            Files.writeString(rendered.resolve(files.group(1)), files.group(2));
        }
        var compiled = GrpcProtoSchema.fromDescriptorSet(Files.readAllBytes(ProtoExampleCompiler.compile(rendered.resolve("service.proto"))));

        // 裁剪后的方法仍可解析出相同的请求和响应形状。
        var method = compiled.method("samples.Orders", "Put");
        assertEquals(1, method.getService().getMethods().size());
        assertFalse(document.contains("Unused"), document);
        assertEquals("samples.data.Envelope.Item", method.getOutputType().getFullName());
        assertTrue(method.getOutputType().findFieldByNumber(1).toProto().getProto3Optional());
        assertEquals("choice", method.getInputType().findFieldByNumber(2).getContainingOneof().getName());
        assertEquals(originalMethod.getInputType().findFieldByNumber(2).getJsonName(), method.getInputType().findFieldByNumber(2).getJsonName());
        assertTrue(method.getInputType().findFieldByNumber(4).isMapField());
    }
}
