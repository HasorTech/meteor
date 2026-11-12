import grpc from '@grpc/grpc-js';
import protobuf from 'protobufjs';
import 'protobufjs/ext/descriptor/index.js';

const root = protobuf.Root.fromDescriptor(Buffer.from('{{descriptors}}', 'base64'));
const Request = root.lookupType('{{requestType}}');
const Response = root.lookupType('{{responseType}}');
const client = new grpc.Client('{{host}}:{{port}}', grpc.credentials.createInsecure());
const metadata = new grpc.Metadata();
metadata.set('tri-service-group', {{group}});
metadata.set('tri-service-version', {{version}});
try {
  const result = await new Promise((resolve, reject) => client.makeUnaryRequest(
    '{{methodPath}}', value => Buffer.from(Request.encode(Request.fromObject(value)).finish()),
    bytes => Response.toObject(Response.decode(bytes), { longs: String, bytes: String }),
    JSON.parse({{requestJson}}), metadata, { deadline: Date.now() + 5000 },
    (error, value) => error ? reject(error) : resolve(value)
  ));
  console.log(result);
} finally {
  client.close();
}
