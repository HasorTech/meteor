    for (;;) {
        uint32_t header;
        if (exact(connection, (unsigned char *)&header, 4)) { close(connection); return 1; }
        uint32_t marker = ntohl(header), size = marker & 0x7fffffff;
        if (size > 16 * 1024 * 1024) { close(connection); return 1; }
        unsigned char *body = (unsigned char *)malloc(size ? size : 1);
        if (!body || exact(connection, body, size)) { free(body); close(connection); return 1; }
        for (uint32_t i = 0; i < size; i++) printf("%02x", body[i]);
        free(body);
        if (marker & 0x80000000) break;
    }
