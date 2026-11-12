    u_long size = xdr_sizeof((xdrproc_t) xdr_{{type}}, &response);
    char *bytes = (char *) malloc(size == 0 ? 1 : size);
    if (bytes == NULL) {
        fputs("Cannot allocate response buffer\n", stderr);
        xdr_free((xdrproc_t) xdr_{{type}}, (char *) &response);
        clnt_destroy(client);
        return 2;
    }
    XDR stream;
    xdrmem_create(&stream, bytes, (u_int) size, XDR_ENCODE);
    if (xdr_{{type}}(&stream, &response)) {
        printf("response XDR (%lu bytes): ", size);
        for (u_long index = 0; index < size; index++) {
            printf("%02x", (unsigned char) bytes[index]);
        }
        putchar('\n');
    }
    xdr_destroy(&stream);
    free(bytes);
