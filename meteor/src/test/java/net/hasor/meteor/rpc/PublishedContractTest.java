/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import java.util.Map;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublishedContractTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void usesParsedRecursiveTypeAfterThePublishedNodesAreDiscarded() throws Exception {
        // 类型图通过引用描述递归 DTO，提供者还发布了实际线上方法名。
        ObjectNode service = JSON.readValue("""
                {
                  "methods": [{
                    "name": "send",
                    "javaSignature": "send(example.Node)",
                    "parameters": [{"type": "example.Node"}],
                    "returns": {"type": "void"}
                  }],
                  "types": {
                    "void": {"javaType": "void"},
                    "int": {"javaType": "int"},
                    "example.Node": {
                      "javaType": "example.Node",
                      "kind": "object",
                      "properties": [
                        {"name": "counter", "javaName": "counter", "type": "int"},
                        {"name": "next", "javaName": "next", "type": "example.Node"}
                      ]
                    }
                  }
                }
                """, ObjectNode.class);
        ObjectNode endpoint = JSON.readValue("""
                {
                  "name": "Service",
                  "serializeType": "Json",
                  "methods": [{"name": "wireSend", "javaSignature": "send(example.Node)"}]
                }
                """, ObjectNode.class);
        PublishedContract contract = new PublishedContract("jsonrpc", "/rpc", service, endpoint);

        // 清空输入后，调用使用已经读取的类型和方法数据。
        service.removeAll();
        endpoint.removeAll();
        Object[] values = { Map.of("next", Map.of()) };
        PublishedContract.Call call = contract.bindCall("send", values);

        // 绑定沿递归引用恢复两层结构，并为未传入的基本类型字段提供零值。
        assertEquals("wireSend", call.operation.methodName());
        assertEquals(void.class, call.operation.returnType());
        MetStruct argument = assertInstanceOf(MetStruct.class, call.arguments[0]);
        MetStruct next = assertInstanceOf(MetStruct.class, argument.get("next"));
        assertEquals("example.Node", next.typeName());
        assertEquals(0, next.get("counter"));
    }

    @Test
    void rejectsAnAmbiguousPublishedSignatureWhenBindingTheCall() throws Exception {
        // 两个协议操作声明了同一个 Java 方法签名。
        ObjectNode service = JSON.readValue("""
                {"methods": [{"name": "ping", "javaSignature": "ping()", "parameters": []}]}
                """, ObjectNode.class);
        ObjectNode endpoint = JSON.readValue("""
                {
                  "name": "Service",
                  "serializeType": "Json",
                  "methods": [
                    {"name": "ping1", "javaSignature": "ping()"},
                    {"name": "ping2", "javaSignature": "ping()"}
                  ]
                }
                """, ObjectNode.class);
        PublishedContract contract = new PublishedContract("jsonrpc", "/rpc", service, endpoint);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, //
                () -> contract.bindCall("ping", new Object[0]));
        assertEquals("Ambiguous published method: ping()", error.getMessage());
    }
}
