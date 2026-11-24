/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.realmeteor.console;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.protocol.grpc.GrpcProtoResource;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles example inputs using the console runtime's Protobuf and Wire dependencies. */
public final class ProtoExampleCompiler {
    private ProtoExampleCompiler() {
    }

    public static Path compile(Path entry) throws Exception {
        Path directory = entry.getParent();
        Path descriptor = entry.resolveSibling(entry.getFileName() + ".pb");
        Path log = entry.resolveSibling("proto-compile.log");
        List<String> command;
        if (StringUtils.equals("true", System.getenv("METEOR_EXAMPLES_NATIVE_MATRIX"))) {
            command = List.of("docker", "run", "--rm", "--user", Files.getAttribute(directory, "unix:uid").toString(), "-v", directory + ":/work", "-w", "/work", "--entrypoint", "protoc", "meteor-test/native:persistent-amd64", "-I", ".", "--include_imports", "--descriptor_set_out=" + descriptor.getFileName(), entry.getFileName().toString());
        } else {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classes = Path.of(ProtoExampleCompiler.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String classpath = System.getProperty("meteor.console.runtimeClasspath") + File.pathSeparator + classes;
            command = List.of(java, "-Djava.io.tmpdir=" + directory, "-cp", classpath, ProtoExampleCompiler.class.getName(), entry.toString(), descriptor.toString());
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Protobuf compiler timed out");
            assertEquals(0, process.exitValue(), Files.readString(log));
            return descriptor;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        var resource = new GrpcProtoResource(Path.of(arguments[0]).toUri().toURL());
        Files.write(Path.of(arguments[1]), GrpcProtoSchema.fromResource(resource).descriptorSet());
    }
}
