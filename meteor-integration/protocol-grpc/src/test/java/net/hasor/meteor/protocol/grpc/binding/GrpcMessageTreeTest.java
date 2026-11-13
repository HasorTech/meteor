/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.protocol.grpc.binding;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.util.JsonFormat;
import net.hasor.meteor.protocol.grpc.GrpcProtoSchema;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrpcMessageTreeTest {
    private static final Descriptor      VALUE = descriptor();
    private static final GrpcMessageTree TREE  = GrpcMessageTree.compile(VALUE, new HashMap<>());

    @Test
    void binaryTreeEncodingMatchesTheOfficialParser() throws Exception {
        // 二进制节点来自 Jackson 对 DTO 的正常转换，直接映射也必须得到相同线协议值。
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("data", new byte[] { 0, 1, -1, 127 });
        DynamicMessage expected = official(source);
        assertEquals(expected, TREE.tryEncode(source));
    }

    @Test
    void binaryTreeDecodingPreservesProtoJsonShape() throws Exception {
        // Object、JsonNode 等开放类型仍观察到 ProtoJSON base64 文本，不能变成 Java byte[]。
        DynamicMessage source = DynamicMessage.newBuilder(VALUE) //
                .setField(VALUE.findFieldByName("data"), ByteString.copyFrom(new byte[] { 0, -1 })) //
                .build();
        assertEquals(officialTree(source), TREE.tryDecode(source));
    }

    @Test
    void unsignedIntegerEncodingPreservesAllBits() throws Exception {
        // uint32 与 uint64 的最大值不能先缩窄成 Java 有符号整数后再校验。
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("uintValue", 0xffffffffL);
        source.put("unsignedLong", new BigInteger("18446744073709551615"));
        assertEquals(official(source), TREE.tryEncode(source));
    }

    @Test
    void unsignedIntegerDecodingMatchesTheOfficialTree() throws Exception {
        // 同时保留 uint32 的数值节点类型，以及 uint64 的十进制字符串形式。
        DynamicMessage source = DynamicMessage.newBuilder(VALUE) //
                .setField(VALUE.findFieldByName("uint_value"), -1) //
                .setField(VALUE.findFieldByName("unsigned_long"), -1L) //
                .build();
        assertEquals(officialTree(source), TREE.tryDecode(source));
    }

    @Test
    void signedIntegerBoundariesMatchTheOfficialParser() throws Exception {
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("integer", Integer.MIN_VALUE);
        source.put("signedLong", Long.MIN_VALUE);
        assertEquals(official(source), TREE.tryEncode(source));
    }

    @Test
    void floatDecodingPreservesTheOfficialDecimalValueAndNodeType() throws Exception {
        // Float 先打印再解析的十进制值与直接扩宽到 double 不同，开放类型也要保持原行为。
        DynamicMessage source = DynamicMessage.newBuilder(VALUE) //
                .setField(VALUE.findFieldByName("ratio"), 0.1f) //
                .build();
        assertEquals(officialTree(source), TREE.tryDecode(source));
    }

    @Test
    void negativeZeroEncodingPreservesTheSignBit() throws Exception {
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("ratio", -0.0f);
        source.put("decimal", -0.0d);
        assertEquals(official(source), TREE.tryEncode(source));
    }

    @Test
    void repeatedAndNestedMessagesMatchTheOfficialParser() throws Exception {
        // 递归描述在编译时共享，运行期每条消息使用独立 Builder。
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.putArray("numbers").add(0.1f).add(-2.5f);
        source.putObject("child").put("text", "nested");
        DynamicMessage result = TREE.tryEncode(source);
        assertEquals(official(source), result);
        assertEquals(officialTree(result), TREE.tryDecode(result));
    }

    @Test
    void conflictingOneofMembersUseTheOfficialValidationPath() throws Exception {
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("first", "one");
        source.put("second", "two");
        assertNull(TREE.tryEncode(source));
        assertThrows(InvalidProtocolBufferException.class, () -> official(source));
    }

    @Test
    void outOfRangeNumbersUseTheOfficialValidationPath() throws Exception {
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("uintValue", -1);
        assertNull(TREE.tryEncode(source));
        assertThrows(InvalidProtocolBufferException.class, () -> official(source));
    }

    @Test
    void numericTextUsesTheOfficialParser() throws Exception {
        // ProtoJSON 允许字符串整数；普通 Java 数值节点以外的形式继续交给官方解析器。
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("signedLong", "9223372036854775807");
        assertNull(TREE.tryEncode(source));
        assertEquals(Long.MAX_VALUE, official(source).getField(VALUE.findFieldByName("signed_long")));
    }

    @Test
    void unknownEnumNumbersUseTheOfficialParser() throws Exception {
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.put("state", 99);
        assertNull(TREE.tryEncode(source));
        DynamicMessage message = official(source);
        assertEquals(officialTree(message), TREE.tryDecode(message));
    }

    @Test
    void mapMessagesKeepTheOfficialConversionPath() throws Exception {
        ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
        source.putObject("entries").put("key", "value");
        assertNull(TREE.tryEncode(source));
        assertNull(TREE.tryDecode(official(source)));
    }

    @Test
    void wellKnownMessagesKeepTheOfficialConversionPath() {
        GrpcMessageTree timestamp = GrpcMessageTree.compile(Timestamp.getDescriptor(), new HashMap<>());
        assertNull(timestamp.tryDecode(Timestamp.newBuilder().setSeconds(1).build()));
    }

    @Test
    void compiledMessageTreesSupportConcurrentInvocations() {
        // 同一绑定被多个会话复用，构建与转换不能在描述图中保留某次调用的数据。
        IntStream.range(0, 200).parallel().forEach(index -> {
            ObjectNode source = GrpcValueMapping.MAPPER.createObjectNode();
            source.put("text", "value-" + index);
            source.put("integer", index);
            try {
                DynamicMessage result = TREE.tryEncode(source);
                assertEquals(official(source), result);
                assertEquals(officialTree(result), TREE.tryDecode(result));
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
    }

    private static DynamicMessage official(JsonNode source) throws Exception {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(VALUE);
        GrpcProtoSchema.mergeJson(source.toString(), builder, JsonFormat.TypeRegistry.newBuilder().build());
        return builder.build();
    }

    private static JsonNode officialTree(Message source) throws Exception {
        return GrpcValueMapping.MAPPER.readTree(JsonFormat.printer().print(source));
    }

    private static Descriptor descriptor() {
        // 字段编号表达显式协议契约；按编号逐项加入，oneof 成员共用编号为零的声明。
        DescriptorProto.Builder value = DescriptorProto.newBuilder().setName("Value");
        value.addField(field("data", 1, FieldDescriptorProto.Type.TYPE_BYTES));
        value.addField(field("text", 2, FieldDescriptorProto.Type.TYPE_STRING));
        value.addField(field("integer", 3, FieldDescriptorProto.Type.TYPE_INT32));
        value.addField(field("uint_value", 4, FieldDescriptorProto.Type.TYPE_UINT32));
        value.addField(field("signed_long", 5, FieldDescriptorProto.Type.TYPE_INT64));
        value.addField(field("unsigned_long", 6, FieldDescriptorProto.Type.TYPE_UINT64));
        value.addField(field("ratio", 7, FieldDescriptorProto.Type.TYPE_FLOAT));
        value.addField(field("decimal", 8, FieldDescriptorProto.Type.TYPE_DOUBLE));
        value.addField(field("enabled", 9, FieldDescriptorProto.Type.TYPE_BOOL));
        value.addField(field("state", 10, FieldDescriptorProto.Type.TYPE_ENUM).setTypeName(".tree.State"));
        value.addField(field("numbers", 11, FieldDescriptorProto.Type.TYPE_FLOAT) //
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));
        value.addField(field("child", 12, FieldDescriptorProto.Type.TYPE_MESSAGE).setTypeName(".tree.Value"));
        value.addOneofDecl(OneofDescriptorProto.newBuilder().setName("choice"));
        value.addField(field("first", 13, FieldDescriptorProto.Type.TYPE_STRING).setOneofIndex(0));
        value.addField(field("second", 14, FieldDescriptorProto.Type.TYPE_STRING).setOneofIndex(0));
        DescriptorProto.Builder entry = DescriptorProto.newBuilder().setName("EntriesEntry") //
                .setOptions(MessageOptions.newBuilder().setMapEntry(true));
        entry.addField(field("key", 1, FieldDescriptorProto.Type.TYPE_STRING));
        entry.addField(field("value", 2, FieldDescriptorProto.Type.TYPE_STRING));
        value.addNestedType(entry);
        value.addField(field("entries", 15, FieldDescriptorProto.Type.TYPE_MESSAGE) //
                .setTypeName(".tree.Value.EntriesEntry").setLabel(FieldDescriptorProto.Label.LABEL_REPEATED));
        EnumDescriptorProto.Builder state = EnumDescriptorProto.newBuilder().setName("State");
        state.addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0));
        state.addValue(EnumValueDescriptorProto.newBuilder().setName("READY").setNumber(1));
        FileDescriptorProto file = FileDescriptorProto.newBuilder().setName("tree.proto").setPackage("tree") //
                .setSyntax("proto3").addMessageType(value).addEnumType(state).build();
        try {
            return FileDescriptor.buildFrom(file, new FileDescriptor[0]).findMessageTypeByName("Value");
        } catch (DescriptorValidationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static FieldDescriptorProto.Builder field(String name, int number, FieldDescriptorProto.Type type) {
        return FieldDescriptorProto.newBuilder().setName(name).setNumber(number).setType(type);
    }
}
