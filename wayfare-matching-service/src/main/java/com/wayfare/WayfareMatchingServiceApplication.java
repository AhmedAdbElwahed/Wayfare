package com.wayfare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient 
public class WayfareMatchingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WayfareMatchingServiceApplication.class, args);
    }
}