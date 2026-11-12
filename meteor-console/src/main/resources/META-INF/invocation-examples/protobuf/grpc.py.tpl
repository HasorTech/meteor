import base64
import json
import grpc
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory, json_format

pool = descriptor_pool.DescriptorPool()
definitions = descriptor_pb2.FileDescriptorSet.FromString(base64.b64decode("{{descriptors}}"))
for definition in definitions.file:
    pool.Add(definition)
Request = message_factory.GetMessageClass(pool.FindMessageTypeByName("{{requestType}}"))
Response = message_factory.GetMessageClass(pool.FindMessageTypeByName("{{responseType}}"))
request = json_format.Parse({{requestJson}}, Request())
with grpc.insecure_channel("{{host}}:{{port}}") as channel:
    call = channel.unary_unary("{{methodPath}}", request_serializer=Request.SerializeToString,
                              response_deserializer=Response.FromString)
    result = call(request, timeout=5, metadata=(("tri-service-group", {{group}}), ("tri-service-version", {{version}})))
    print(json_format.MessageToJson(result))
