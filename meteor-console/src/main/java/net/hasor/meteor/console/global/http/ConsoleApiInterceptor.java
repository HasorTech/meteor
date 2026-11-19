/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.http;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.global.auth.ConsoleCookies;
import net.hasor.meteor.console.global.auth.SessionService;
import net.hasor.meteor.console.global.error.ConsoleHttpException;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.web.HandlerInterceptor;
import net.hasor.web.Invoker;

/** API 的认证、同源校验和正文解析。 */
public final class ConsoleApiInterceptor implements HandlerInterceptor {
    public static final  String                   USERNAME = ConsoleApiInterceptor.class.getName() + ".username";
    private static final int                      MAX_BODY = 1024 * 1024;
    private final        Supplier<SessionService> sessions;
    private final        Supplier<ConsoleCookies> cookies;

    public ConsoleApiInterceptor(Supplier<SessionService> sessions, Supplier<ConsoleCookies> cookies) {
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Override
    public boolean preHandle(Invoker invoker) throws Exception {
        String path = invoker.getRequestPath();
        if (!path.startsWith("/api/")) {
            return true;
        }

        HttpServletRequest request = invoker.getHttpRequest();
        Locale locale = ConsoleI18n.getLocale();

        invoker.getHttpResponse().setHeader("Cache-Control", "no-store");
        invoker.getHttpResponse().setHeader("X-Content-Type-Options", "nosniff");
        invoker.getHttpResponse().addHeader("Vary", "Accept-Language");
        invoker.getHttpResponse().setHeader("Content-Language", locale.toLanguageTag());

        String method = request.getMethod();
        boolean reading = StringUtils.equals("GET", method);
        if (!reading) {
            this.requireSameOrigin(request);
        }

        boolean languageBundle = reading && StringUtils.equals("/api/i18n", path);
        boolean login = StringUtils.equals("POST", method) && StringUtils.equals("/api/auth/login", path);
        if (!login && !languageBundle) {
            String username = this.sessions.get().authenticate(this.cookies.get().token(request));
            request.setAttribute(USERNAME, username);
        }

        JsonNode body = JsonUtils.createObjectNode();
        if (!reading) {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) {
                throw new ConsoleHttpException(413, ConsoleMessageKey.ERROR_REQUEST_BODY_TOO_LARGE);
            }
            if (bytes.length > 0) {
                String type = request.getContentType();
                if (type == null || !type.startsWith("application/json")) {
                    throw new ConsoleHttpException(415, ConsoleMessageKey.ERROR_UNSUPPORTED_CONTENT_TYPE);
                }
                body = JsonUtils.readTree(bytes);
                if (body == null || !body.isObject()) {
                    throw new ErrorMessageException(ConsoleMessageKey.ERROR_REQUEST_BODY_MUST_BE_OBJECT);
                }
            }
        }

        String query = request.getQueryString();
        URI uri = URI.create(path + (query == null ? "" : "?" + query));
        String managementToken = request.getHeader("X-Meteor-Management-Token");
        request.setAttribute(ConsoleRequest.class.getName(), new ConsoleRequest(uri, body, managementToken, locale));
        return true;
    }

    private void requireSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String site = request.getHeader("Sec-Fetch-Site");
        String host = request.getHeader("Host");
        boolean foreign = origin != null && !StringUtils.equals(URI.create(origin).getAuthority(), host);
        if (foreign || StringUtils.equals("cross-site", site)) {
            throw new ConsoleHttpException(403, ConsoleMessageKey.ERROR_CROSS_SITE_OPERATIONS_ARE_NOT_ALLOWED);
        }
    }
}
