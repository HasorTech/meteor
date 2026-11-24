{{jsBody}}
const response = await fetch({{url}}, {
  method: 'POST', headers: {{headers}}, body, signal: AbortSignal.timeout(5000)
});
if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
console.log(Array.from(new Uint8Array(await response.arrayBuffer()), byte => byte.toString(16).padStart(2, '0')).join(''));
