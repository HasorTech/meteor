#include <curl/curl.h>
#include <stdio.h>
static size_t result(char *data, size_t size, size_t count, void *unused) {
    (void)unused;
    for (size_t i = 0; i < size * count; i++) printf("%02x", (unsigned char)data[i]);
    return size * count;
}
int main(void) {
    /* {{operation}}；请求来自当前服务的实际线上契约。 */
    unsigned char body[] = { {{bytes}} };
    if (curl_global_init(CURL_GLOBAL_DEFAULT) != CURLE_OK) return 1;
    CURL *client = curl_easy_init();
    if (!client) { curl_global_cleanup(); return 1; }
    struct curl_slist *headers = NULL;
{{curlHeaders}}
    curl_easy_setopt(client, CURLOPT_URL, {{url}});
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, body);
    curl_easy_setopt(client, CURLOPT_POSTFIELDSIZE, (long)sizeof(body));
    curl_easy_setopt(client, CURLOPT_HTTP_VERSION, CURL_HTTP_VERSION_2_PRIOR_KNOWLEDGE);
    curl_easy_setopt(client, CURLOPT_TIMEOUT_MS, 5000L);
    curl_easy_setopt(client, CURLOPT_WRITEFUNCTION, result);
    curl_easy_setopt(client, CURLOPT_FAILONERROR, 1L);
    CURLcode status = curl_easy_perform(client);
    if (status != CURLE_OK) fprintf(stderr, "%s\n", curl_easy_strerror(status));
    curl_slist_free_all(headers);
    curl_easy_cleanup(client);
    curl_global_cleanup();
    return status == CURLE_OK ? 0 : 1;
}
