package com.atsmatcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AtsMatcherApplication {
    public static void main(String[] args) {
        System.setProperty("user.timezone", "UTC");
        SpringApplication.run(AtsMatcherApplication.class, args);
    }
}
