from pathlib import Path
import grpc
from google.protobuf import descriptor_pb2, descriptor_pool, message_factory, json_format

pool = descriptor_pool.DescriptorPool()
definitions = {{pythonDescriptors}}
for definition in definitions.file:
    pool.Add(definition)
Request = message_factory.GetMessageClass(pool.FindMessageTypeByName("{{requestType}}"))
Response = message_factory.GetMessageClass(pool.FindMessageTypeByName("{{responseType}}"))
request = json_format.Parse(Path("request.json").read_text(encoding="utf-8"), Request())
with grpc.insecure_channel("{{host}}:{{port}}") as channel:
    call = channel.unary_unary("{{methodPath}}", request_serializer=Request.SerializeToString,
                              response_deserializer=Response.FromString)
    print(json_format.MessageToJson(call(request, timeout=5{{pythonMetadata}})))
