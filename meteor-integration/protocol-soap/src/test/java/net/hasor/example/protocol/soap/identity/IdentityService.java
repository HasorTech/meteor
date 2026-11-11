/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.example.protocol.soap.identity;
import net.hasor.meteor.annotation.MetMethod;
import net.hasor.meteor.annotation.MetParam;
import net.hasor.meteor.annotation.MetService;

@MetService(name = "IdentityService")
public interface IdentityService {
    @MetMethod(id = 1)
    IdentityMessage echo(@MetParam(id = 1) IdentityMessage request);
}
