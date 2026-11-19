/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.rpc;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetFilter;
import net.hasor.meteor.MetFilterChain;
import net.hasor.meteor.MetRequest;
import net.hasor.meteor.MetResponse;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages.Message;
import net.hasor.meteor.console.global.error.ConsoleHttpException;
import net.hasor.meteor.console.global.error.ConsoleOperationException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.domain.MetException;
import net.hasor.meteor.domain.ProtocolStatus;
import net.hasor.meteor.registry.RegistryTokenProvider;

/** Console RPC 的逐次凭据校验和管理操作边界。 */
public final class RpcManagementFilter implements MetFilter {
    private final RegistryTokenProvider tokens;
    private final String[]              secrets;

    public RpcManagementFilter(RegistryTokenProvider tokens, String... secrets) {
        this.tokens = tokens;
        this.secrets = secrets;
    }

    @Override
    public void doFilter(MetRequest request, MetResponse response, MetFilterChain chain) throws Throwable {
        String expected;
        try {
            expected = this.tokens.getToken();
        } catch (RuntimeException failure) {
            send(request, response, ProtocolStatus.Unauthorized, ConsoleMessageKey.ERROR_RPC_AUTHENTICATION_UNAVAILABLE);
            return;
        }

        String supplied = request.getOption(RpcManagement.CREDENTIAL_OPTION);
        boolean blank = StringUtils.isBlank(expected) || StringUtils.isBlank(supplied);
        if (blank || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            send(request, response, ProtocolStatus.Unauthorized, ConsoleMessageKey.ERROR_RPC_AUTHENTICATION_FAILED);
            return;
        }

        Object[] arguments = request.getParameterObject();
        if (arguments.length == 0 || !(arguments[0] instanceof RpcTarget target)) {
            send(request, response, (short) 400, ConsoleMessageKey.ERROR_RPC_REGISTRY_TARGET_REQUIRED);
            return;
        }

        try {
            target.validate();
            chain.doFilter(request, response);
        } catch (Exception failure) {
            Throwable cause = failure;
            Throwable visible = null;
            while ((cause instanceof ConsoleOperationException || cause instanceof InvocationTargetException || cause instanceof UndeclaredThrowableException || cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null) {
                if (visible == null && cause instanceof ConsoleOperationException) {
                    visible = cause;
                }
                cause = cause.getCause();
            }
            if (visible == null) {
                visible = cause;
            }

            short status = ProtocolStatus.InvokeError;
            if (cause instanceof MetException remote) {
                status = remote.getStatus();
            } else if (cause instanceof ConsoleHttpException http) {
                status = (short) http.status();
            } else if (cause instanceof IllegalArgumentException) {
                status = 400;
            } else if (cause instanceof SecurityException) {
                status = ProtocolStatus.Forbidden;
            } else if (cause instanceof NoSuchElementException) {
                status = ProtocolStatus.NotFound;
            } else if (cause instanceof UnsupportedOperationException || cause instanceof ConcurrentModificationException) {
                status = 409;
            } else if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
                status = ProtocolStatus.Timeout;
            }
            // The downstream diagnostic is already sanitized. Also mask the inbound token.
            String diagnostic = this.redact(ConsoleErrorMessages.getMessage(visible, Locale.US), expected);
            Message message = ConsoleErrorMessages.messageOf(visible);
            if (message != null) {
                message = message.redact(value -> this.redact(value, expected));
            }
            RpcManagementErrors.send(request, response, status, message, diagnostic);
        }
    }

    private static void send(MetRequest request, MetResponse response, short status, ConsoleMessageKey key) {
        Message message = new Message(key);
        RpcManagementErrors.send(request, response, status, message, message.format(Locale.US));
    }

    private String redact(String message, String expected) {
        String sanitized = message.replace(expected, "***");
        if (this.secrets != null) {
            for (String secret : this.secrets) {
                if (StringUtils.isNotBlank(secret)) {
                    sanitized = sanitized.replace(secret, "***");
                }
            }
        }
        return sanitized;
    }
}
