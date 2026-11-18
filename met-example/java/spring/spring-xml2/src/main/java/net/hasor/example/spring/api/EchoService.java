/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.spring.api;
import net.hasor.meteor.annotation.MetService;

@MetService(name = "EchoService", group = "default", version = "1.0.0")
public interface EchoService {
    String echo(String text);
}
