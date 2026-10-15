package com.careroute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CarerouteApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarerouteApplication.class, args);
    }
}
