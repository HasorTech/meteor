/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.domain.payload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import net.hasor.cobble.concurrent.future.BasicFuture;
import net.hasor.cobble.concurrent.future.Future;
import net.hasor.meteor.connector.ProtocolProvider.Target;

/**
 * RSF Request 的化身,是封装 Request 的数据对象。
 * @version : 2014年10月25日
 * @author 赵永春 (zyc@hasor.net)
 */
public class RequestPayload extends Payload {
    // Local lifecycle only: protocol codecs do not encode this notification.
    private final transient BasicFuture<Void> completion     = new BasicFuture<>();
    private transient       boolean           encodedArguments;
    private transient       Target            protocolTarget;
    private transient       String            protocolPath;
    private transient       Object            protocolBinding;
    private                 long              requestID      = 0;    //请求ID
    private                 short             flags          = 0;    //标签
    private                 long              receiveTime    = 0;    //接收请求（本地时间戳）
    private                 String            serviceName    = null; //远程服务名
    private                 String            serviceGroup   = null; //远程服务分组
    private                 String            serviceVersion = null; //远程服务版本
    private                 String            targetMethod   = null; //远程服务方法名
    private                 String            serializeType  = null; //序列化策略
    private                 int               clientTimeout  = 0;    //远程调用时最大忍受等待时间
    private                 boolean           isMessage      = false;//是否为消息请求
    private                 List<String>      paramTypes     = null; //参数类型
    private                 List<Object>      paramValues    = null; //参数值

    public RequestPayload() {
        super(Type.REQUEST);
        this.paramTypes = new ArrayList<>();
        this.paramValues = new ArrayList<>();
    }

    /** Invocation completion, independent of the future returned by sendData. */
    public Future<Void> completion() {
        return this.completion;
    }

    /** Release local protocol correlation after success, failure, timeout or cancellation. */
    public void complete(Throwable cause) {
        if (cause == null) {
            this.completion.completed(null);
        } else {
            this.completion.failed(cause);
        }
    }

    /** The argument values contain serialized bytes until the provider resolves the method. */
    public boolean hasEncodedArguments() {
        return this.encodedArguments;
    }

    public void markArgumentsEncoded() {
        this.encodedArguments = true;
    }

    public void setProtocolBinding(Target target, String path, Object binding) {
        this.protocolTarget = target;
        this.protocolPath = path;
        this.protocolBinding = binding;
    }

    /** Reuse the codec binding prepared for this call. */
    public <T, E extends Exception> T resolveProtocolBinding(Class<T> type, OperationResolver<T, E> resolver) throws E {
        if (this.protocolBinding == null) {
            this.protocolBinding = resolver.resolve();
        }
        return type.cast(this.protocolBinding);
    }

    public <T> T getProtocolBinding(Class<T> type) {
        return type.cast(this.protocolBinding);
    }

    public <T, E extends Exception> T resolveOperationValue(Function<Target, T> bound, OperationResolver<T, E> fallback) throws E {
        if (this.protocolTarget == null) {
            return fallback.resolve();
        } else {
            return bound.apply(this.protocolTarget);
        }
    }

    public String resolveRequestPath(Supplier<String> path) {
        if (this.protocolPath == null) {
            return path.get();
        } else {
            return this.protocolPath;
        }
    }

    public JavaType resolveReturnType(OperationResolver<JavaType, IOException> resolver) throws IOException {
        if (this.protocolTarget == null) {
            return resolver.resolve();
        } else {
            return TypeFactory.defaultInstance().constructType(this.protocolTarget.returnType());
        }
    }

    @FunctionalInterface
    public interface OperationResolver<T, E extends Exception> {
        T resolve() throws E;
    }

    /**获取请求ID。*/
    public long getRequestID() {
        return this.requestID;
    }

    /**设置请求ID。*/
    public void setRequestID(long requestID) {
        this.requestID = requestID;
    }

    /**获取flag*/
    public short getFlags() {
        return this.flags;
    }

    /**设置flag。*/
    public void setFlags(short flags) {
        this.flags = flags;
    }

    /**数据包到达时间*/
    public void setReceiveTime(long receiveTime) {
        this.receiveTime = receiveTime;
    }

    /**数据包到达时间*/
    public long getReceiveTime() {
        return this.receiveTime;
    }

    /**获取服务名*/
    public String getServiceName() {
        return this.serviceName;
    }

    /**设置服务名*/
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    /**获取服务分组*/
    public String getServiceGroup() {
        return this.serviceGroup;
    }

    /**设置服务分组*/
    public void setServiceGroup(String serviceGroup) {
        this.serviceGroup = serviceGroup;
    }

    /**获取服务版本*/
    public String getServiceVersion() {
        return this.serviceVersion;
    }

    /**设置服务版本*/
    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    /**获取调用的方法名*/
    public String getTargetMethod() {
        return this.targetMethod;
    }

    /**设置调用的方法名*/
    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    /**获取序列化类型*/
    public String getSerializeType() {
        return this.serializeType;
    }

    /**设置序列化类型*/
    public void setSerializeType(String serializeType) {
        this.serializeType = serializeType;
    }

    /**获取远程客户端调用超时时间。*/
    public int getClientTimeout() {
        return this.clientTimeout;
    }

    /**设置远程客户端调用超时时间。*/
    public void setClientTimeout(int clientTimeout) {
        this.clientTimeout = clientTimeout;
    }

    /** 是否为消息请求；消息请求只等待协议完成提交，不等待业务最终响应。 */
    public boolean isMessage() {
        return this.isMessage;
    }

    /** 设置是否为消息请求；确认完成调用后，远端业务继续执行且不再发送业务最终响应。 */
    public void setMessage(boolean message) {
        this.isMessage = message;
    }
    //

    /** 按方法形参声明顺序追加参数，类型和值列表的相同下标必须对应同一参数。 */
    public void addParameter(String paramType, Object paramValue) {
        this.paramTypes.add(paramType);
        this.paramValues.add(paramValue);
    }

    /**获取请求参数类型列表。*/
    public List<String> getParameterTypes() {
        return this.paramTypes;
    }

    /**获取请求参数值列表。*/
    public List<Object> getParameterValues() {
        return this.paramValues;
    }
}