    unsigned char response[65535];
    ssize_t size = recv(connection, response, sizeof(response), 0);
    if (size < 0) { close(connection); return 1; }
    for (ssize_t i = 0; i < size; i++) printf("%02x", response[i]);
