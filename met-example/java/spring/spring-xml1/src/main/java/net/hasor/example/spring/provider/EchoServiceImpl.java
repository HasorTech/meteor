/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.spring.provider;
import net.hasor.example.spring.api.EchoService;

public class EchoServiceImpl implements EchoService {
    @Override
    public String echo(String text) {
        return "Hello, " + text + "!";
    }
}
