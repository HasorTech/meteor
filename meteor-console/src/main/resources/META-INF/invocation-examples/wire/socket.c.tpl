#include <sys/socket.h>
#include <sys/time.h>
#include <netdb.h>
#include <unistd.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
{{reader}}int main(void) {
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
    FILE *input = fopen("request.bin", "rb");
    if (!input) { close(connection); return 1; }
    fseek(input, 0, SEEK_END);
    long requestSize = ftell(input);
    rewind(input);
    if (requestSize < 0) { fclose(input); close(connection); return 1; }
    unsigned char *request = (unsigned char *)malloc((size_t)requestSize);
    if (!request || fread(request, 1, (size_t)requestSize, input) != (size_t)requestSize) {
        free(request); fclose(input); close(connection); return 1;
    }
    fclose(input);
    size_t sent = 0;
    while (sent < (size_t)requestSize) {
        ssize_t count = send(connection, request + sent, (size_t)requestSize - sent, 0);
        if (count <= 0) { free(request); close(connection); return 1; }
        sent += count;
    }
    free(request);
{{receive}}
    puts(""); close(connection);
    return 0;
}
