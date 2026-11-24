from pathlib import Path
from struct import pack
from google.protobuf import descriptor_pb2, descriptor_pool, json_format, message_factory

schema = {{pythonDescriptors}}
pool = descriptor_pool.DescriptorPool()
for definition in schema.file:
    pool.Add(definition)
Request = message_factory.GetMessageClass(pool.FindMessageTypeByName("{{requestType}}"))
request = json_format.Parse(Path("request.json").read_text(encoding="utf-8"), Request()).SerializeToString()
Path("request.bin").write_bytes(pack(">BI", 0, len(request)) + request)
