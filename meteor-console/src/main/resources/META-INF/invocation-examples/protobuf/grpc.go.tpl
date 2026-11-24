package main

import (
    "context"
    "fmt"
    "os"
    "time"
    "google.golang.org/grpc"
    "google.golang.org/grpc/credentials/insecure"
{{goMetadataImport}}    "google.golang.org/protobuf/encoding/protojson"
{{goDescriptorImport}}    "google.golang.org/protobuf/reflect/protodesc"
    "google.golang.org/protobuf/reflect/protoreflect"
    "google.golang.org/protobuf/types/descriptorpb"
    "google.golang.org/protobuf/types/dynamicpb"
)

func main() {
    raw, err := os.ReadFile("{{goDescriptorFile}}")
    if err != nil { panic(err) }
    definitions := &descriptorpb.FileDescriptorSet{}
    if err = {{goDescriptorUnmarshal}}(raw, definitions); err != nil { panic(err) }
    files, err := protodesc.NewFiles(definitions)
    if err != nil { panic(err) }
    requestType, err := files.FindDescriptorByName("{{requestType}}")
    if err != nil { panic(err) }
    responseType, err := files.FindDescriptorByName("{{responseType}}")
    if err != nil { panic(err) }
    request := dynamicpb.NewMessage(requestType.(protoreflect.MessageDescriptor))
    payload, err := os.ReadFile("request.json")
    if err != nil { panic(err) }
    if err = protojson.Unmarshal(payload, request); err != nil { panic(err) }
    response := dynamicpb.NewMessage(responseType.(protoreflect.MessageDescriptor))
    connection, err := grpc.NewClient("{{host}}:{{port}}", grpc.WithTransportCredentials(insecure.NewCredentials()))
    if err != nil { panic(err) }
    defer connection.Close()
    ctx, cancel := context.WithTimeout(context.Background(), 5 * time.Second)
    defer cancel()
{{goMetadata}}    if err = connection.Invoke(ctx, "{{methodPath}}", request, response); err != nil { panic(err) }
    fmt.Println(protojson.Format(response))
}
