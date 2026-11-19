/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.protocol.triple;
import java.util.concurrent.Flow;
import net.hasor.meteor.annotation.MetMethod;

/** 不同流形状使用独立的对外操作名。 */
public interface TripleMixedShapeService {
    String source(int value);

    @MetMethod(name = "sourceStream")
    Flow.Publisher<String> source(String prefix);

    String sink(int value);

    @MetMethod(name = "sinkStream")
    String sink(Flow.Publisher<String> values);
}
