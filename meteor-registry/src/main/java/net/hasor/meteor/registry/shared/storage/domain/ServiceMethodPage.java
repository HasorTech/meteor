/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.shared.storage.domain;

/** 方法目录页；不支持枚举的存储只接受精确方法名查询。 */
public class ServiceMethodPage extends RecordPage<EntServiceMethodData> {
    private boolean listingSupported = true;

    public boolean isListingSupported() {
        return this.listingSupported;
    }

    public void setListingSupported(boolean listingSupported) {
        this.listingSupported = listingSupported;
    }
}
