/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.integration;
import net.hasor.meteor.annotation.MetAddress;

public class EchoClient {
    @MetAddress("rsf://127.0.0.1:28880")
    private EchoService echo;

    public void run() {
        System.out.println(this.echo.echo("Meteor"));
    }
}
