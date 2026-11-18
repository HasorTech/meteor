/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.oncrpc.binding;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class OncRpcIdlResourceTest {
    @Test
    public void loadClasspathDefinition() throws Exception {
        // 通过 Cobble 解析 classpath 中的 RPC/XDR 定义文件。
        OncRpcIdlResource resource = OncRpcIdlResource.load("classpath:/xdr/service.x");

        assertTrue(resource.getSources().get("service.x").contains("program"));
    }

    @Test
    public void loadFileUrlDefinition() throws Exception {
        // 测试资源由 Gradle 复制到本地目录，使用 file: URL 加载同一份定义。
        URL source = this.getClass().getResource("/xdr/service.x");
        assertNotNull(source);
        assertEquals("file", source.getProtocol());
        OncRpcIdlResource resource = OncRpcIdlResource.load(source.toExternalForm());

        assertTrue(resource.getSources().get("service.x").contains("program"));
    }

    @Test
    public void missingDefinitionReportsLocation() {
        // 资源查找失败时报告调用者给出的路径，不把 null 传给后续解析器。
        IOException failure = assertThrows(IOException.class, () -> OncRpcIdlResource.load("classpath:missing-OncRpcIdlResource.definition"));

        assertTrue(failure.getMessage().contains("classpath:missing-OncRpcIdlResource.definition"));
    }

    @Test
    public void malformedDefinitionIsRejectedAtLoad(@TempDir Path directory) throws Exception {
        // 不发布服务，直接加载语法损坏的文件；错误需保留入口位置和解析原因。
        Path source = directory.resolve("invalid.definition");
        Files.writeString(source, "program Echo {\n version V { string ECHO(string) = 1; } = 1;");
        String location = source.toUri().toString();

        IOException failure = assertThrows(IOException.class, () -> OncRpcIdlResource.load(location));

        assertTrue(failure.getMessage().contains(location));
        assertNotNull(failure.getCause());
    }

    @Test
    public void differentFormatIsRejectedAtLoad(@TempDir Path directory) throws Exception {
        // 文件可以是合法 XML，但不能作为当前协议的定义文件加载。
        Path source = directory.resolve("invalid.definition");
        Files.writeString(source, "<html/>");
        String location = source.toUri().toString();

        IOException failure = assertThrows(IOException.class, () -> OncRpcIdlResource.load(location));

        assertTrue(failure.getMessage().contains(location));
        assertNotNull(failure.getCause());
    }
}
