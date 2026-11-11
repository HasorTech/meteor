/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.example.protocol.triple.identity;
import java.io.Serializable;
import net.hasor.meteor.annotation.MetField;

public class IdentityMessage implements Serializable {
    @MetField(id = 1)
    private String value = "input";

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
