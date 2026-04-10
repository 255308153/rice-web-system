package com.rice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RicePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(RicePlatformApplication.class, args);
    }
}
