import net from 'node:net';
import dgram from 'node:dgram';
// {{operation}}；请求来自当前服务的实际线上契约，响应为十六进制报文。
const request = Buffer.from("{{body}}", 'base64');
if ({{udp}}) {
  const connection = dgram.createSocket('udp4');
  const timer = setTimeout(() => { connection.close(); throw new Error('RPC timeout'); }, 5000);
  connection.once('error', error => { clearTimeout(timer); connection.close(); throw error; });
  connection.once('message', response => { clearTimeout(timer); console.log(response.toString('hex')); connection.close(); });
  connection.send(request, {{port}}, {{host}});
} else {
  const connection = net.connect({host: {{host}}, port: {{port}}}, () => connection.write(request));
  connection.setTimeout(5000, () => connection.destroy(new Error('RPC timeout')));
  let buffered = Buffer.alloc(0), result = Buffer.alloc(0);
  connection.on('error', error => { throw error; });
  connection.on('data', chunk => {
    buffered = Buffer.concat([buffered, chunk]);
    while (buffered.length >= 4) {
      const marker = buffered.readUInt32BE(0), length = marker & 0x7fffffff;
      if (buffered.length < length + 4) return;
      result = Buffer.concat([result, buffered.subarray(4, length + 4)]);
      buffered = buffered.subarray(length + 4);
      if ({{thrift}} || marker >>> 31) { console.log(result.toString('hex')); connection.end(); return; }
    }
  });
}
