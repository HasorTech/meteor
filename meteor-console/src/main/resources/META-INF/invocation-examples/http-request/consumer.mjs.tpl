const address = {{url}};
// 当前服务的线上请求。浏览器调用时需与服务同源，或由服务允许当前来源。
const body = {{body}};
const response = await fetch(address, {
  method: 'POST', headers: {{headers}}, body, signal: AbortSignal.timeout(5000)
});
const result = await response.text();
if (!response.ok) throw new Error(`HTTP ${response.status}: ${result}`);
console.log(result);
