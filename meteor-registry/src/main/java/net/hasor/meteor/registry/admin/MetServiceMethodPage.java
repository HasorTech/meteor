/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 */
package net.hasor.meteor.registry.admin;
import java.util.ArrayList;
import java.util.List;
import net.hasor.meteor.registry.admin.description.MetServiceDescription;

/** 持久方法目录；每个定义只含一个方法及其类型图，不代表当前实例的可用方法。 */
public class MetServiceMethodPage {
    private boolean                     listingSupported = true;
    private String                      nextCursor;
    private List<MetServiceDescription> definitions      = new ArrayList<>();

    public boolean isListingSupported() {
        return this.listingSupported;
    }

    public void setListingSupported(boolean listingSupported) {
        this.listingSupported = listingSupported;
    }

    public String getNextCursor() {
        return this.nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public List<MetServiceDescription> getDefinitions() {
        return this.definitions;
    }

    public void setDefinitions(List<MetServiceDescription> definitions) {
        this.definitions = definitions;
    }
}
