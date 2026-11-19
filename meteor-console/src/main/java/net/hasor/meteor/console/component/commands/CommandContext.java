/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.commands;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.component.provider.ProviderConnection;
import net.hasor.meteor.console.component.provider.ProviderFactories;
import net.hasor.meteor.console.global.error.ConsoleOperationException;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.domain.MetException;
import net.hasor.meteor.domain.ProtocolStatus;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.RegistryTokenProvider;
import net.hasor.meteor.registry.TargetAdmin;
import net.hasor.meteor.registry.admin.*;

/** 一条管理连接的执行环境；管理独占 Provider，直连时只借用 TargetAdmin。 */
public final class CommandContext implements AutoCloseable {
    private final ClusterProfile               profile;
    private final Callable<ProviderConnection> opener;
    private final TargetAdmin                  direct;
    private       ProviderConnection           connection;
    private       boolean                      closed;

    private CommandContext(ClusterProfile profile, Callable<ProviderConnection> opener, TargetAdmin direct) {
        this.profile = profile;
        this.opener = opener;
        this.direct = direct;
    }

    /** 首次执行命令时打开独占连接，每次管理调用仍读取当前凭据。 */
    static CommandContext open(ClusterProfile profile, RegistryTokenProvider credentials, ProviderFactories factories) {
        return new CommandContext(profile, () -> factories.open(profile, credentials), null);
    }

    /** 接管已打开连接，即使没有执行命令，关闭时也会释放它。 */
    static CommandContext own(ProviderConnection connection) {
        Objects.requireNonNull(connection, "connection");
        CommandContext context = new CommandContext(null, () -> connection, null);
        context.connection = connection;
        return context;
    }

    /** 借用直连管理入口，不关闭它或其所属上下文。 */
    static CommandContext direct(TargetAdmin direct) {
        return new CommandContext(null, null, Objects.requireNonNull(direct, "direct"));
    }

    synchronized <R> R execute(CommandRequest<R> request, RegistryCommand<CommandRequest<R>, R> command) {
        return this.execute(() -> {
            if (request.getTarget().isDirect() && this.direct == null) {
                throw new ErrorMessageException(ConsoleMessageKey.ERROR_DIRECT_INSTANCE_OPERATIONS_REQUIRE_THE_DIRECT);
            }
            return command.execute(this, request);
        });
    }

    synchronized <R> R withRegistry(Function<RegistryAdmin, R> operation) {
        return this.execute(() -> operation.apply(this.registry()));
    }

    private <R> R execute(Callable<R> operation) {
        if (this.closed) {
            throw ErrorMessageException.conflict(ConsoleMessageKey.ERROR_THE_CONNECTION_SETTINGS_CHANGED_REFRESH_AND);
        }
        try {
            if (this.connection == null && this.opener != null) {
                this.connection = this.opener.call();
            }
            return operation.call();
        } catch (Exception failure) {
            throw this.redact(failure);
        }
    }

    /** 返回借用的注册中心入口；生命周期由当前 Context 负责。 */
    public RegistryAdmin registry() {
        if (this.connection == null) {
            throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_THE_CONSOLE_STARTED_IN_DIRECT_MODE);
        }
        return this.connection.registry();
    }

    public MetClusterAdmin cluster() {
        return this.registry().getClusterAdmin();
    }

    public MetInstanceAdmin instance(ManagementTarget request) {
        if (request.isDirect()) {
            return this.direct.withTokenProvider(request::getManagementToken).getInstanceAdminByAddress(request.getTarget());
        } else {
            return this.registry().getInstanceAdmin(request.requireInstanceId());
        }
    }

    public MetServiceAdmin service(ManagementTarget request) {
        String serviceId = request.requireServiceId();
        if (request.isDirect()) {
            return this.direct.withTokenProvider(request::getManagementToken).getServiceAdminByAddress(request.getTarget(), serviceId);
        }

        String instanceId = request.getInstanceId();
        if (StringUtils.isNotBlank(instanceId)) {
            return this.registry().getServiceAdmin(serviceId, instanceId);
        } else {
            return this.registry().getServiceAdmin(serviceId);
        }
    }

    public String scope(ManagementTarget request) {
        String scope = request.requireScope();
        if (StringUtils.equals("cluster", scope) && request.isDirect()) {
            throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_DIRECT_MODE_DOES_NOT_HAVE_CLUSTER);
        }
        return scope;
    }

    public MetValidationResult validateRule(ManagementTarget request, MetRuleType type, String rule) {
        return switch (this.scope(request)) {
            case "cluster" -> this.cluster().validateRule(type, rule);
            case "service" -> this.service(request).validateRule(type, rule);
            case "instance" -> throw ErrorMessageException.unsupported(ConsoleMessageKey.ERROR_INSTANCE_MANAGEMENT_DOES_NOT_PROVIDE_STANDALONE);
            default -> throw new ErrorMessageException(ConsoleMessageKey.ERROR_UNKNOWN_GOVERNANCE_SCOPE);
        };
    }

    private ConsoleOperationException redact(Exception failure) {
        Throwable visible = failure;
        while ((visible instanceof UndeclaredThrowableException || visible instanceof InvocationTargetException || visible instanceof ExecutionException || visible instanceof CompletionException) && visible.getCause() != null) {
            visible = visible.getCause();
        }
        Throwable cause = visible;
        while (cause instanceof ConsoleOperationException && cause.getCause() != null) {
            cause = cause.getCause();
        }

        if (cause instanceof MetException remote && remote.getStatus() == ProtocolStatus.Unauthorized) {
            // Both presentation boundaries retain the original RPC status.
            ConsoleMessageKey message = this.direct == null ? //
                    ConsoleMessageKey.ERROR_REMOTE_CONSOLE_AUTHENTICATION_FAILED : //
                    ConsoleMessageKey.ERROR_INSTANCE_AUTHENTICATION_FAILED;
            return new ConsoleOperationException(message, visible);
        }
        return new ConsoleOperationException(visible, this::redactMessage);
    }

    private String redactMessage(String message) {
        if (this.profile != null) {
            if (StringUtils.isNotBlank(this.profile.getPassword())) {
                message = message.replace(this.profile.getPassword(), "***");
            }
            if (StringUtils.isNotBlank(this.profile.getManagementToken())) {
                message = message.replace(this.profile.getManagementToken(), "***");
            }
        }
        return message;
    }

    @Override
    public synchronized void close() {
        if (!this.closed) {
            this.closed = true;
            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (Exception failure) {
                    throw this.redact(failure);
                }
            }
        }
    }

}
