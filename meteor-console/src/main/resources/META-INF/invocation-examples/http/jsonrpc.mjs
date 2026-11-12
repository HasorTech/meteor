const url = {{url}};
const call = {{call}};
const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: call.method, params: call.args }),
    signal: AbortSignal.timeout(15000),
});
if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
const result = await response.json();
if (result.error) throw new Error(JSON.stringify(result.error));
console.log(JSON.stringify(result.result, null, 2));
