/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.example.basic.options;
import net.hasor.meteor.rpc.MetRequestLocal;
import net.hasor.meteor.rpc.MetResponseLocal;

public class PriceServiceImpl implements PriceService {
    @Override
    public String quote(String sku) {
        String requestId = new MetRequestLocal().getOption("request-id");
        System.out.println("报价服务 request-id=" + requestId + ", sku=" + sku);
        new MetResponseLocal().addOption("processed-by", "price-service");
        return "100.00";
    }
}
