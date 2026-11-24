const response = await fetch({{url}}, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: {{method}}, params: {{arguments}} }),
  signal: AbortSignal.timeout(15000)
});
if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
const { result, error } = await response.json();
if (error) throw new Error(JSON.stringify(error));
console.log(JSON.stringify(result));
