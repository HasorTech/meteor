package main

import (
    "context"
    "encoding/base64"
    "fmt"
    "time"
    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
    "google.golang.org/grpc/metadata"
    "google.golang.org/protobuf/encoding/protojson"
    "google.golang.org/protobuf/proto"
    "google.golang.org/protobuf/reflect/protodesc"
    "google.golang.org/protobuf/reflect/protoreflect"
    "google.golang.org/protobuf/types/descriptorpb"
    "google.golang.org/protobuf/types/dynamicpb"
)

func main() {
    raw, err := base64.StdEncoding.DecodeString("{{descriptors}}")
    if err != nil { panic(err) }
    definitions := &descriptorpb.FileDescriptorSet{}
    if err = proto.Unmarshal(raw, definitions); err != nil { panic(err) }
    files, err := protodesc.NewFiles(definitions)
    if err != nil { panic(err) }
    requestType, err := files.FindDescriptorByName("{{requestType}}")
    if err != nil { panic(err) }
    responseType, err := files.FindDescriptorByName("{{responseType}}")
    if err != nil { panic(err) }
    request := dynamicpb.NewMessage(requestType.(protoreflect.MessageDescriptor))
    if err = protojson.Unmarshal([]byte({{requestJson}}), request); err != nil { panic(err) }
    response := dynamicpb.NewMessage(responseType.(protoreflect.MessageDescriptor))
    connection, err := grpc.NewClient("{{host}}:{{port}}", grpc.WithTransportCredentials(insecure.NewCredentials()))
    if err != nil { panic(err) }
    defer connection.Close()
    ctx, cancel := context.WithTimeout(context.Background(), 5 * time.Second)
    defer cancel()
    ctx = metadata.AppendToOutgoingContext(ctx, "tri-service-group", {{group}}, "tri-service-version", {{version}})
    if err = connection.Invoke(ctx, "{{methodPath}}", request, response); err != nil { panic(err) }
    fmt.Println(protojson.Format(response))
}
