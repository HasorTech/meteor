/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.rpc;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetResponse;
import net.hasor.meteor.domain.MetRequestObject;
import net.hasor.meteor.domain.MetRuntimeUtils;
import net.hasor.meteor.domain.MetServiceType;
import net.hasor.meteor.domain.ServiceDomain;
import net.hasor.meteor.domain.payload.RequestPayload;
import net.hasor.meteor.domain.payload.ResponsePayload;

/**
 * 构造 RPC 请求、响应和状态通知的 Payload。
 * @version : 2015年3月28日
 * @author 赵永春 (zyc@hasor.net)
 */
final class MetPayloadBuilder {
    private MetPayloadBuilder() {
    }

    /**将{@link MetRequestObject},转换为{@link RequestPayload}。*/
    static RequestPayload buildRequestPayload(MetRequestObject rsfRequest) {
        RequestPayload info = new RequestPayload();
        MetBindInfo<?> rsfBindInfo = rsfRequest.getBindInfo();
        String serializeType = rsfRequest.getSerializeType();

        //1.基本信息
        info.setRequestID(rsfRequest.getRequestID());//请求ID
        info.setServiceGroup(rsfBindInfo.getBindGroup());//序列化策略
        info.setServiceName(rsfBindInfo.getBindName());//序列化策略
        info.setServiceVersion(rsfBindInfo.getBindVersion());//序列化策略
        ProtocolOperation operation = (ProtocolOperation) rsfRequest.getProtocolOperation();
        info.setTargetMethod(operation == null ? rsfRequest.getMethod().getName() : operation.methodName());
        if (operation != null) {
            operation.apply(info);
        }
        info.setSerializeType(serializeType);//序列化策略
        info.setClientTimeout(rsfRequest.getTimeout());
        info.setMessage(rsfRequest.isMessage());

        //2.params
        Object[] pObjects = rsfRequest.getParameterObject();
        pObjects = (pObjects == null) ? new Object[0] : pObjects;
        Class<?>[] pTypes = rsfRequest.getParameterTypes();
        int parameterCount = operation == null ? pTypes.length : rsfRequest.getWireParameterTypes().size();
        for (int i = 0; i < parameterCount; i++) {
            String typeByte = operation == null ? MetRuntimeUtils.toAsmType(pTypes[i]) : rsfRequest.getWireParameterTypes().get(i);
            info.addParameter(typeByte, pObjects[i]);
        }

        //3.Opt参数
        info.addOptionMap(rsfRequest);
        return info;
    }

    static MetRequestObject buildPublishedRequest(long id, MetContext context, MetGenericService target, String method, //
            PublishedContract.Call call) {
        ServiceDomain<Object> binding = new ServiceDomain<>(Object.class);
        binding.setBindGroup(target.getGroup());
        binding.setBindName(target.getName());
        binding.setBindVersion(target.getVersion());
        binding.setServiceType(MetServiceType.Consumer);
        binding.setDistributed(true);
        binding.addBindProtocol(target.getProtocol());
        binding.setSerializeType(call.serializeType);

        MetRequestObject request = new MetRequestObject(id, true, context, binding, null, call.arguments);
        request.setProtocolOperation(method, call.operation, call.parameterTypes);
        request.setSerializeType(call.serializeType);
        return request;
    }

    static ResponsePayload buildResponseStatus(long requestID, short status, String errorInfo) {
        ResponsePayload info = new ResponsePayload();
        info.setRequestID(requestID);
        info.setStatus(status);
        if (StringUtils.isNotBlank(errorInfo)) {
            info.addOption("message", errorInfo);
        }
        return info;
    }

    /**将{@link MetResponse},转换为{@link ResponsePayload}。*/
    static ResponsePayload buildResponsePayload(MetResponse rsfResponse) {
        ResponsePayload info = new ResponsePayload();
        String serializeType = rsfResponse.getSerializeType();
        info.setRequestID(rsfResponse.getRequestID());
        info.setStatus(rsfResponse.getStatus());
        info.setSerializeType(serializeType);
        info.setReturnType(rsfResponse.getReturnType().getName());
        info.setReturnData(rsfResponse.getData());
        info.setOptions(rsfResponse);
        return info;
    }
}
