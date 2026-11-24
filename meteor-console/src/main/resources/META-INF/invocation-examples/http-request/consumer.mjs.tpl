const response = await fetch({{url}}, {
  method: 'POST', headers: {{headers}}, body: {{body}}, signal: AbortSignal.timeout(5000)
});
const result = await response.text();
if (!response.ok) throw new Error(`HTTP ${response.status}: ${result}`);
console.log(result);
