/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import net.hasor.cobble.ResourcesUtils;

/** 本地 .proto 资源入口；构造时解析并校验，不生成 Java 类，也不参与注册中心同步。 */
public final class GrpcProtoResource {
    final GrpcProtoSchema schema;

    /** 加载并校验 classpath:、file: 或 URL 指定的定义文件；加载失败时抛出 IOException。 */
    public static GrpcProtoResource load(String location) throws IOException {
        URL source = ResourcesUtils.getResource(location);
        if (source == null) {
            throw new IOException("gRPC proto resource not found: " + location);
        }

        try {
            return new GrpcProtoResource(source);
        } catch (IOException | IllegalArgumentException failure) {
            String message = "Failed to load gRPC proto resource: " + location + ": " + failure.getMessage();
            throw new IOException(message, failure);
        }
    }

    public GrpcProtoResource(URL source) throws IOException {
        this.schema = GrpcProtoSchema.load(source);
    }

    public Map<String, String> getSources() {
        return this.schema.getSources();
    }
}
