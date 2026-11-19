/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.global.auth;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import com.fasterxml.jackson.core.type.TypeReference;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DeserializationException;
import io.jsonwebtoken.io.Deserializer;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;
import net.hasor.cobble.StringUtils;
import net.hasor.core.Init;
import net.hasor.core.Inject;
import net.hasor.meteor.console.global.MeteorConfig;
import net.hasor.meteor.console.global.error.ErrorMessageException;
import net.hasor.meteor.console.global.i18n.ConsoleI18n;
import net.hasor.meteor.console.global.i18n.ConsoleMessageKey;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.RegistryTokenProvider;
import net.hasor.web.Invoker;
import net.hasor.web.invoker.HttpParameters;

/** Cookie 承载签名 JWT；服务端只记录退出后的令牌，直到它们过期。 */
public final class SessionService implements RegistryTokenProvider {
    public static final  long              TTL     = 1800_000;
    private static final String            ISSUER  = "meteor-console";
    private static final TokenJsonCodec    JSON    = new TokenJsonCodec();
    @Inject
    private              MeteorConfig      config;
    @Inject
    private              ConsoleCookies    cookies;
    private              String            username;
    private              String            password;
    private final        SecretKey         key     = Jwts.SIG.HS256.key().build();
    private final        JwtParser         parser;
    private final        Map<String, Long> revoked = new ConcurrentHashMap<>();

    public SessionService() {
        // 每次启动生成签名密钥，重启后需要重新登录。
        this.parser = Jwts.parser().json(JSON).verifyWith(this.key).requireIssuer(ISSUER) //
                .sig().clear().add(Jwts.SIG.HS256).and().build();
    }

    @Init
    public void initialize() {
        if (!this.config.isSecurityEnabled()) {
            return;
        }
        String account = this.config.getSecurityAccount();
        int separator = account == null ? -1 : account.indexOf(':');
        if (separator < 0) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_METEOR_WEB_AUTH_ACCOUNT_MUST_USE);
        }

        this.username = account.substring(0, separator).trim();
        this.password = account.substring(separator + 1);
        if (StringUtils.isBlank(this.username) || StringUtils.isBlank(this.password)) {
            throw new ErrorMessageException(ConsoleMessageKey.ERROR_SET_METEOR_WEB_AUTH_ACCOUNT_USERNAME);
        }
    }

    public boolean isSecurityEnabled() {
        return this.config.isSecurityEnabled();
    }

    /** 本地注册中心管理复用当前请求的登录认证，不保存某个用户的登录令牌。 */
    @Override
    public String getToken() {
        Invoker invoker = HttpParameters.localInvoker();
        if (invoker == null) {
            throw new AuthenticationException(ConsoleMessageKey.ERROR_REGISTRY_MANAGEMENT_REQUIRES_AN_ACTIVE_CONSOLE);
        }
        String token = this.cookies.token(invoker.getHttpRequest());
        this.authenticate(token);
        // 只有用户显式关闭登录认证时，才允许匿名管理。
        return this.config.isSecurityEnabled() ? token : "anonymous-console-session";
    }

    public String login(String previousToken, String username, String password) {
        byte[] expected = this.password.getBytes(StandardCharsets.UTF_8);
        boolean matches = MessageDigest.isEqual(expected, password.getBytes(StandardCharsets.UTF_8));
        if (!StringUtils.equals(this.username, username) || !matches) {
            throw new AuthenticationException(ConsoleMessageKey.ERROR_INVALID_LOGIN);
        }

        // 登录时轮换当前浏览器的令牌，其他浏览器的会话继续有效。
        this.logout(previousToken);
        long now = System.currentTimeMillis();
        return Jwts.builder().json(JSON).issuer(ISSUER).subject(username).id(UUID.randomUUID().toString()) //
                .issuedAt(new Date(now)).expiration(new Date(now + TTL)).signWith(this.key, Jwts.SIG.HS256).compact();
    }

    public String authenticate(String token) {
        if (!this.config.isSecurityEnabled()) {
            return "";
        }
        Claims claims = this.claims(token);
        if (this.revoked.containsKey(claims.getId())) {
            throw new AuthenticationException(ConsoleMessageKey.ERROR_SIGN_IN_TO_THE_CONSOLE);
        }
        return claims.getSubject();
    }

    public void logout(String token) {
        long now = System.currentTimeMillis();
        this.revoked.values().removeIf(expires -> expires <= now);
        if (!this.config.isSecurityEnabled() || StringUtils.isBlank(token)) {
            return;
        }
        try {
            Claims claims = this.claims(token);
            this.revoked.put(claims.getId(), claims.getExpiration().getTime());
        } catch (AuthenticationException ignored) {
            // 已失效或不属于本控制台的令牌无需登记。
        }
    }

    private Claims claims(String token) {
        try {
            Claims claims = this.parser.parseSignedClaims(token).getPayload();
            boolean identified = StringUtils.equals(this.username, claims.getSubject());
            boolean dated = claims.getIssuedAt() != null && claims.getExpiration() != null;
            if (!identified || !dated || StringUtils.isBlank(claims.getId())) {
                throw new AuthenticationException(ConsoleMessageKey.ERROR_SIGN_IN_TO_THE_CONSOLE);
            }
            return claims;
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new AuthenticationException(ConsoleMessageKey.ERROR_SIGN_IN_TO_THE_CONSOLE);
        }
    }

    /** 将 JJWT 的 JSON 接口接入控制台共用工具。 */
    private static final class TokenJsonCodec implements Serializer<Map<String, ?>>, Deserializer<Map<String, ?>> {
        private static final TypeReference<Map<String, ?>> FIELDS = new TypeReference<>() {
        };

        @Override
        public byte[] serialize(Map<String, ?> value) {
            try {
                return JsonUtils.writeValueAsBytes(value);
            } catch (IOException failure) {
                throw new SerializationException(ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_JWT_JSON_ENCODE, Locale.US), failure);
            }
        }

        @Override
        public void serialize(Map<String, ?> value, OutputStream output) {
            try {
                JsonUtils.writeValue(output, value);
            } catch (IOException failure) {
                throw new SerializationException(ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_JWT_JSON_ENCODE, Locale.US), failure);
            }
        }

        @Override
        public Map<String, ?> deserialize(byte[] json) {
            try {
                return JsonUtils.readValue(json, FIELDS);
            } catch (IOException failure) {
                throw new DeserializationException(ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_JWT_JSON_DECODE, Locale.US), failure);
            }
        }

        @Override
        public Map<String, ?> deserialize(Reader json) {
            try {
                return JsonUtils.readValue(json, FIELDS);
            } catch (IOException failure) {
                throw new DeserializationException(ConsoleI18n.getMessage(ConsoleMessageKey.ERROR_JWT_JSON_DECODE, Locale.US), failure);
            }
        }
    }
}
