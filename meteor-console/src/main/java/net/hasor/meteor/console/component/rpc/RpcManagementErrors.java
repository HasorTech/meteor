/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.rpc;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.meteor.MetRequest;
import net.hasor.meteor.MetResponse;
import net.hasor.meteor.console.global.error.ConsoleErrorMessages.Message;
import net.hasor.meteor.console.global.error.ConsoleOperationException;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.domain.MetException;

/** Carries keyed diagnostics between Console peers using the existing management RPC status message. */
public final class RpcManagementErrors {
    public static final  String FORMAT_OPTION  = "console.management.error-format";
    public static final  String FORMAT_VERSION = "1";
    private static final String PREFIX         = "console-error-v1:";

    private RpcManagementErrors() {
    }

    static void send(MetRequest request, MetResponse response, short status, Message message, String diagnostic) {
        if (message != null && FORMAT_VERSION.equals(request.getOption(FORMAT_OPTION))) {
            try {
                String encoded = JsonUtils.writeValueAsString(Map.of("key", message.key().name(), "arguments", message.arguments(Locale.US), "message", diagnostic));
                response.sendStatus(status, PREFIX + encoded);
                return;
            } catch (JsonProcessingException ignored) {
                // Send the sanitized diagnostic if the structured message cannot be encoded.
            }
        }
        response.sendStatus(status, diagnostic);
    }

    public static ConsoleOperationException receive(MetException failure, UnaryOperator<String> redact) {
        String prefix = "(" + failure.getStatus() + ") - " + PREFIX;
        String diagnostic = failure.getMessage();
        if (diagnostic == null || !diagnostic.startsWith(prefix)) {
            return new ConsoleOperationException(failure, redact);
        }

        try {
            JsonNode envelope = JsonUtils.readTree(diagnostic.substring(prefix.length()));
            if (envelope != null && envelope.isObject() && envelope.path("key").isTextual()) {
                Message message = decode(envelope);
                if (message != null) {
                    return new ConsoleOperationException(failure, message, redact);
                }
                if (envelope.path("message").isTextual()) {
                    // A peer may define a new key that this Console does not recognize yet.
                    MetException fallback = new MetException(failure.getStatus(), envelope.path("message").textValue(), failure);
                    return new ConsoleOperationException(fallback, redact);
                }
            }
        } catch (JsonProcessingException ignored) {
            // Keep the original RPC diagnostic when the peer did not send a valid envelope.
        }
        return new ConsoleOperationException(failure, redact);
    }

    private static Message decode(JsonNode envelope) {
        ConsoleMessageKey key;
        try {
            key = ConsoleMessageKey.valueOf(envelope.path("key").textValue());
        } catch (IllegalArgumentException unknownKey) {
            return null;
        }

        JsonNode values = envelope.path("arguments");
        if (!values.isArray()) {
            return null;
        }
        String[] arguments = new String[values.size()];
        for (int i = 0; i < arguments.length; i++) {
            JsonNode value = values.get(i);
            if (!value.isTextual()) {
                return null;
            }
            arguments[i] = value.textValue();
        }
        return new Message(key, (Object[]) arguments);
    }
}
