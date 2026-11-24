import net from 'node:net';
import { readFileSync } from 'node:fs';

const socket = net.connect({ host: {{host}}, port: {{port}} });
socket.setTimeout(5000, () => socket.destroy(new Error('RPC timeout')));
socket.write(readFileSync('request.bin'));
let pending = Buffer.alloc(0);
let complete = false;
try {
  for await (const chunk of socket) {
    pending = Buffer.concat([pending, chunk]);
{{receive}}
  }
  if (!complete) throw new Error('Incomplete RPC response');
} finally {
  socket.destroy();
}
