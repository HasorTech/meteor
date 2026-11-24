import grpc from '@grpc/grpc-js';
import protobuf from 'protobufjs';
{{jsDescriptorImport}}
import { readFileSync } from 'node:fs';

{{jsDescriptors}}
const Request = root.lookupType('{{requestType}}');
const Response = root.lookupType('{{responseType}}');
const client = new grpc.Client('{{host}}:{{port}}', grpc.credentials.createInsecure());
{{jsMetadata}}try {
  const result = await new Promise((resolve, reject) => client.makeUnaryRequest(
    '{{methodPath}}', value => Buffer.from(Request.encode(Request.fromObject(value)).finish()),
    bytes => Response.toObject(Response.decode(bytes), { longs: String, bytes: String }),
    JSON.parse(readFileSync('request.json', 'utf8')){{jsMetadataArgument}}, { deadline: Date.now() + 5000 },
    (error, value) => error ? reject(error) : resolve(value)
  ));
  console.log(result);
} finally {
  client.close();
}
