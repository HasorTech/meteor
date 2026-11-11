package main

import (
	"context"
	"log"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"meteor.example/orders"
)

func run() error {
	// Add the protocol context, default group and version before the generated method path.
	interceptor := func(ctx context.Context, method string, request, reply any,
		connection *grpc.ClientConn, invoke grpc.UnaryInvoker, options ...grpc.CallOption) error {
		return invoke(ctx, "/grpc/default/1.0.0"+method, request, reply, connection, options...)
	}

	connection, err := grpc.NewClient("127.0.0.1:8080", grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithUnaryInterceptor(interceptor))
	if err != nil {
		return err
	}
	defer connection.Close()
	client := orders.NewOrderDemoServiceClient(connection)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	// Save one order and fetch that same order through two ordinary RPC methods.
	order := orders.Sample()
	saved, err := client.Save(ctx, order)
	if err != nil {
		return err
	}
	found, err := client.Find(ctx, &orders.OrderQuery{Id: order.Id})
	if err != nil {
		return err
	}
	return orders.Print(saved, found)
}

func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}
