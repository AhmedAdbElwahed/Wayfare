package com.wayfare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

import com.wayfare.grpc.driver.DriverServiceGrpc;

@Configuration
public class GrpcClientConfig {

    /** Channel {@code driver-service} = {@code spring.grpc.client.channel.driver-service.*}. */
    @Bean
    public DriverServiceGrpc.DriverServiceBlockingStub driverServiceStub(GrpcChannelFactory channels) {
        return DriverServiceGrpc.newBlockingStub(channels.createChannel("driver-service"));
    }
}
