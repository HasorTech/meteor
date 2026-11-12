/* 调用控制台当前选中的真实 ONC RPC 端点，使用 service.x 的原生 XDR 契约。 */
#include <arpa/inet.h>
#include <netdb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "service.h"
{{undefinitions}}

#define PROGRAM_ID {{program}}UL
#define VERSION_ID {{version}}UL

int main(void) {
    struct addrinfo hints = {0};
    struct addrinfo *resolved = NULL;
    hints.ai_family = AF_INET;
    int lookup = getaddrinfo({{host}}, NULL, &hints, &resolved);
    if (lookup != 0) {
        fprintf(stderr, "Cannot resolve provider: %s\n", gai_strerror(lookup));
        return 2;
    }
    struct sockaddr_in address = *(struct sockaddr_in *) resolved->ai_addr;
    freeaddrinfo(resolved);
    address.sin_port = htons({{port}});
    int socket_fd = RPC_ANYSOCK;
{{connect}}
    if (client == NULL) {
        clnt_pcreateerror("Cannot connect to provider");
        return 2;
    }
    /* 按实际业务调整参数，字段布局和过程编号由此端点的契约决定。 */
{{request}}{{response}}
    struct timeval timeout = {10, 0};
    enum clnt_stat status = clnt_call(client, {{procedure}}UL,
            (xdrproc_t) {{requestXdr}}, (caddr_t) &request,
            (xdrproc_t) {{responseXdr}}, (caddr_t) &response, timeout);
    xdr_free((xdrproc_t) {{requestXdr}}, (char *) &request);
    if (status != RPC_SUCCESS) {
        clnt_perror(client, "RPC failed");
        xdr_free((xdrproc_t) {{responseXdr}}, (char *) &response);
        clnt_destroy(client);
        return 2;
    }
{{printResult}}
    xdr_free((xdrproc_t) {{responseXdr}}, (char *) &response);
    clnt_destroy(client);
    return 0;
}
