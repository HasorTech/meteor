/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package net.hasor.meteor.protocol.xmlrpc.codec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.domain.ProtocolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

class XmlRpcFactoryReuseTest {
    @Test
    @Timeout(20)
    void concurrentDocumentsKeepReadersAndValuesIndependent() throws Exception {
        XmlRpcWireCodec codec = new XmlRpcWireCodec(ProtocolConfig.newConfig("xmlrpc", "xmlrpc", Map.of()));
        var workers = Executors.newFixedThreadPool(8);
        try {
            // 同一 codec 同时解析不同文档，factory 复用不能混用 reader 或参数值。
            List<Future<?>> results = new ArrayList<>();
            for (int request = 0; request < 64; request++) {
                String value = "request-" + request;
                results.add(workers.submit(() -> {
                    XmlRpcCall call = codec.decodeRequest(request(value));
                    assertEquals("echo", call.methodName());
                    assertEquals(List.of(value), call.parameters());
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void malformedDocumentDoesNotPoisonTheNextReader() throws Exception {
        XmlRpcWireCodec codec = new XmlRpcWireCodec(ProtocolConfig.newConfig("xmlrpc", "xmlrpc", Map.of()));
        byte[] malformed = "<methodCall><methodName>broken".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> codec.decodeRequest(malformed));
        assertEquals(List.of("valid"), codec.decodeRequest(request("valid")).parameters());
    }

    @Test
    void rejectedExternalEntityDoesNotChangeFollowingDocuments() throws Exception {
        XmlRpcWireCodec codec = new XmlRpcWireCodec(ProtocolConfig.newConfig("xmlrpc", "xmlrpc", Map.of()));
        String xml = "<!DOCTYPE methodCall [<!ENTITY x SYSTEM 'file:///must-not-open'>]>";
        String body = xml + "<methodCall><methodName>&x;</methodName></methodCall>";
        byte[] forbidden = body.getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> codec.decodeRequest(forbidden));
        assertEquals(List.of("safe"), codec.decodeRequest(request("safe")).parameters());
    }

    private static byte[] request(String value) {
        String body = "<methodCall><methodName>echo</methodName><params><param><value><string>";
        body += value + "</string></value></param></params></methodCall>";
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
