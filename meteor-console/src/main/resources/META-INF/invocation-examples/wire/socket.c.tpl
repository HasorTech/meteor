#include <stdbool.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netdb.h>
#include <unistd.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>
static int exact(int connection, unsigned char *data, size_t size) {
    while (size) {
        ssize_t count = recv(connection, data, size, 0);
        if (count <= 0) return -1;
        data += count; size -= count;
    }
    return 0;
}
int main(void) {
    /* {{operation}}；请求来自当前服务的实际线上契约，响应为十六进制报文。 */
    unsigned char request[] = { {{bytes}} };
    struct addrinfo hints = {0}, *addresses = NULL;
    hints.ai_socktype = {{socketKind}};
    if (getaddrinfo({{host}}, "{{port}}", &hints, &addresses)) return 1;
    int connection = socket(addresses->ai_family, addresses->ai_socktype, addresses->ai_protocol);
    if (connection < 0) { freeaddrinfo(addresses); return 1; }
    struct timeval timeout = {5, 0};
    setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(connection, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    if (connect(connection, addresses->ai_addr, addresses->ai_addrlen)) { close(connection); freeaddrinfo(addresses); return 1; }
    freeaddrinfo(addresses);
    size_t sent = 0;
    while (sent < sizeof(request)) {
        ssize_t count = send(connection, request + sent, sizeof(request) - sent, 0);
        if (count <= 0) { close(connection); return 1; }
        sent += count;
    }
    if ({{udp}}) {
        unsigned char result[65535];
        ssize_t count = recv(connection, result, sizeof(result), 0);
        if (count < 0) { close(connection); return 1; }
        for (ssize_t i = 0; i < count; i++) printf("%02x", result[i]);
    } else {
        while (1) {
            uint32_t header;
            if (exact(connection, (unsigned char *)&header, 4)) { close(connection); return 1; }
            uint32_t marker = ntohl(header), size = marker & 0x7fffffff;
            if (size > 16 * 1024 * 1024) { close(connection); return 1; }
            unsigned char *body = (unsigned char *)malloc(size ? size : 1);
            if (!body || exact(connection, body, size)) { free(body); close(connection); return 1; }
            for (uint32_t i = 0; i < size; i++) printf("%02x", body[i]);
            free(body);
            if ({{thrift}} || (marker & 0x80000000)) break;
        }
    }
    puts(""); close(connection);
    return 0;
}
