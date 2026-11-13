/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.protocol.rsf.codec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RsfV1WireBufferTest {
    @Test
    void copyingReadableBytesDoesNotConsumeTheSource() {
        // 源缓冲区已有读取偏移，目标只追加剩余内容，不能把已读前缀写入帧体。
        RsfV1WireBuffer source = RsfV1WireBuffer.output().writeBytes(new byte[] { 1, 2, 3 });
        source.skipBytes(1);
        RsfV1WireBuffer target = RsfV1WireBuffer.output().writeByte(9);
        target.writeBytes(source);
        assertArrayEquals(new byte[] { 9, 2, 3 }, target.toByteArray());
        assertEquals(2, source.readableBytes());
    }

    @Test
    void selfCopyRemainsCorrectWhenTheBufferGrows() {
        // 重叠源与目标在扩容后仍指向同一个新数组，复制必须保留源偏移。
        byte[] values = new byte[100];
        for (int index = 0; index < values.length; index++) {
            values[index] = (byte) index;
        }
        RsfV1WireBuffer buffer = RsfV1WireBuffer.output().writeBytes(values);
        buffer.skipBytes(70);
        buffer.writeBytes(buffer);
        byte[] expected = new byte[60];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = (byte) (70 + index % 30);
        }
        assertArrayEquals(expected, buffer.toByteArray());
    }

    @Test
    void truncatedSliceDoesNotAdvanceTheReader() {
        RsfV1WireBuffer buffer = RsfV1WireBuffer.input(new byte[] { 1, 2 });
        assertThrows(IllegalArgumentException.class, () -> buffer.readBytes(3));
        assertEquals(2, buffer.readableBytes());
        assertArrayEquals(new byte[] { 1, 2 }, buffer.readBytes(2));
        assertEquals(0, buffer.readableBytes());
    }
}
