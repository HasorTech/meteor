static int exact(int connection, unsigned char *data, size_t size) {
    while (size) {
        ssize_t count = recv(connection, data, size, 0);
        if (count <= 0) return -1;
        data += count; size -= count;
    }
    return 0;
}
