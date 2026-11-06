/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.address;
import java.net.*;
import java.util.*;
import net.hasor.cobble.StringUtils;
import net.hasor.cobble.logging.Logger;
import net.hasor.cobble.logging.LoggerFactory;

/**
 * 服务地址例：“rsf://127.0.0.1:8000”
 * @author 赵永春 (zyc@hasor.net)
 * @version : 2014年9月12日
 */
public final class InterAddress {
    private static final Logger logger         = LoggerFactory.getLogger(InterAddress.class);
    public static final  String DEFAULT_SCHEMA = "rsf";
    private final        String schema;                                              //协议
    private final        String path;                                                //完整挂载路径
    private final        String hostAddress;                                         //地址
    private final        int    hostPort;                                            //端口
    private final        String hostSchema;

    public InterAddress(String newAddressURL) throws URISyntaxException {
        this(new URI(newAddressURL));
    }

    public InterAddress(URI newAddressURL) {
        this(newAddressURL, false);
    }

    private InterAddress(URI newAddressURL, boolean local) {
        if (!checkFormat(newAddressURL, local)) {
            throw new IllegalStateException(newAddressURL + " format error.");
        }

        this.schema = newAddressURL.getScheme().toLowerCase(Locale.ROOT);
        this.path = newAddressURL.getRawPath() == null ? "" : newAddressURL.getRawPath();
        this.hostAddress = newAddressURL.getHost().toLowerCase(Locale.ROOT);
        this.hostPort = newAddressURL.getPort();
        this.hostSchema = this.schema + "://" + hostPort(this.hostAddress, this.hostPort) + this.path;
    }

    /** 使用默认 RSF 协议创建无挂载路径的接入地址。 */
    public InterAddress(String hostAddress, int hostPort) {
        this(DEFAULT_SCHEMA, hostAddress, hostPort);
    }

    /** 使用指定协议创建无挂载路径的接入地址。 */
    public InterAddress(String schema, String hostAddress, int hostPort) {
        this(schema, hostAddress, hostPort, "");
    }

    public InterAddress(String hostAddress, int hostPort, String path) {
        this(DEFAULT_SCHEMA, hostAddress, hostPort, path);
    }

    public InterAddress(String schema, String hostAddress, int hostPort, String path) {
        this(addressURI(schema, hostAddress, hostPort, path));
    }

    /** 创建无挂载路径的本地绑定地址，允许端口为零。 */
    public static InterAddress forBinding(String schema, String host, int port) {
        return forBinding(schema, host, port, "");
    }

    /** Local endpoint configuration may use port zero; remote service addresses must use a concrete port. */
    public static InterAddress forBinding(String schema, String host, int port, String path) {
        return new InterAddress(addressURI(schema, host, port, path), true);
    }

    private static URI addressURI(String schema, String host, int port, String path) {
        Objects.requireNonNull(schema, "schema is null.");
        Objects.requireNonNull(host, "hostAddress is null.");

        try {
            String suffix = path;
            if (suffix == null) {
                suffix = "";
            }
            if (!suffix.isEmpty() && !suffix.startsWith("/")) {
                suffix = "/" + suffix;
            }
            return new URI(schema + "://" + hostPort(host, port) + suffix);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid service address", e);
        }
    }

    private static String hostPort(String host, int port) {
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return host + ":" + port;
    }

    /** 返回协议头 */
    public String getSchema() {
        return this.schema;
    }

    /** 返回完整挂载路径；无路径时返回空字符串。 */
    public String getPath() {
        return this.path;
    }

    /** 返回目标IP地址 */
    public String getHost() {
        if (StringUtils.equalsIgnoreCase("local", this.hostAddress)) {
            List<String> localIpAddr = localIpAddr();
            if (localIpAddr.isEmpty()) {
                try {
                    return InetAddress.getLocalHost().getHostName();
                } catch (Exception e) {
                    return "localhost";
                }
            } else {
                return localIpAddr.get(0);
            }
        }
        return this.hostAddress;
    }

    /** 返回IPv4地址 */
    public String getIp() throws UnknownHostException {
        return InetAddress.getByName(getHost()).getHostAddress();
    }

    /** 返回目标地址的端口号 */
    public int getPort() {
        return this.hostPort;
    }

    /** 返回IP地址和端口，格式为：192.168.25.33:8000 */
    public String getHostPort() {
        return hostPort(getHost(), getPort());
    }

    /** 返回IP地址和端口，格式为：192.168.25.33:8000 */
    public String getIpPort() throws UnknownHostException {
        return hostPort(getIp(), getPort());
    }

    /** 转换地址为URL形式 */
    public URI toURI() throws URISyntaxException {
        return new URI(this.hostSchema);
    }

    /** 返回RSF协议形式表述的主机地址。格式为：“rsf://127.0.0.1:8000” */
    public String toHostSchema() {
        return this.hostSchema;
    }

    /** 转换成{@link SocketAddress}类型对象 */
    public InetSocketAddress toSocketAddress() throws UnknownHostException {
        return new InetSocketAddress(getIp(), getPort());
    }

    /**
     * 两个 Address 可以比较是否相等
     * @param obj 另一个对象
     * @return 返回结果。
     */
    public boolean equals(Object obj) {
        if (obj instanceof InterAddress) {
            String diffURI = ((InterAddress) obj).toHostSchema();
            return StringUtils.equals(diffURI, this.toHostSchema());
        } else {
            return false;
        }
    }

    /** 判断连接地址是否是同一个。判断依据是参数的{@link #getHostPort()}返回值和该对象的{@link #getHostPort()}返回值做比较 */
    public boolean equalsHost(InterAddress evalResult) throws UnknownHostException {
        return evalResult != null && equalsHost(evalResult.getIpPort());
    }

    /** 判断连接地址是否是同一个。判断依据是参数值和{@link #getHostPort()}返回值做比较 */
    public boolean equalsHost(String evalResult) throws UnknownHostException {
        if (evalResult == null) {
            return false;
        }
        if (StringUtils.equalsIgnoreCase(this.getHostPort(), evalResult)) {
            return true;
        }

        return StringUtils.equalsIgnoreCase(this.getIpPort(), evalResult);
    }

    @Override
    public int hashCode() {
        return this.hostSchema.hashCode();
    }

    public String toString() {
        return toHostSchema();
    }

    public static boolean checkFormat(URI serviceURL) {
        return checkFormat(serviceURL, false);
    }

    private static boolean checkFormat(URI serviceURL, boolean local) {
        if (serviceURL == null) {
            return false;
        }

        boolean missingHost = StringUtils.isBlank(serviceURL.getHost());
        boolean missingScheme = StringUtils.isBlank(serviceURL.getScheme());
        int minimumPort = local ? 0 : 1;
        int port = serviceURL.getPort();
        boolean invalidPort = port < minimumPort || port > 65535;
        if (missingScheme || missingHost || invalidPort) {
            return false;
        }

        // 地址不携带单元。查询串、片段和用户信息不属于 RPC 接入点身份。
        return serviceURL.getRawUserInfo() == null && serviceURL.getRawQuery() == null && serviceURL.getRawFragment() == null;
    }

    /** 获取本机地址 */
    private static List<String> localIpAddr() {
        List<String> ipList = new ArrayList<>();
        try {
            Enumeration<?> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) interfaces.nextElement();
                Enumeration<?> ipAddrEnum = ni.getInetAddresses();
                while (ipAddrEnum.hasMoreElements()) {
                    InetAddress addr = (InetAddress) ipAddrEnum.nextElement();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    if (ip.contains(":")) {
                        continue;//skip the IPv6 addr
                    }

                    logger.debug("Interface: " + ni.getName() + ", IP: " + ip);
                    ipList.add(ip);
                }
            }
            Collections.sort(ipList);
        } catch (Exception e) {
            logger.error("Failed to get local ip list. " + e.getMessage());
            throw new RuntimeException("Failed to get local ip list");
        }
        return ipList;
    }
}
