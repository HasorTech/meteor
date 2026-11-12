#include <curl/curl.h>
#include <stdio.h>

int main(void) {
    if (curl_global_init(CURL_GLOBAL_DEFAULT) != CURLE_OK) return 1;
    CURL *client = curl_easy_init();
    if (!client) { curl_global_cleanup(); return 1; }
    struct curl_slist *headers = NULL;
{{curlHeaders}}
    const char *body = {{body}};
    curl_easy_setopt(client, CURLOPT_URL, {{url}});
    curl_easy_setopt(client, CURLOPT_POSTFIELDS, body);
    curl_easy_setopt(client, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(client, CURLOPT_TIMEOUT_MS, 5000L);
    curl_easy_setopt(client, CURLOPT_FAILONERROR, 1L);
    CURLcode result = curl_easy_perform(client);
    if (result != CURLE_OK) fprintf(stderr, "%s\n", curl_easy_strerror(result));
    curl_slist_free_all(headers);
    curl_easy_cleanup(client);
    curl_global_cleanup();
    return result == CURLE_OK ? 0 : 1;
}
