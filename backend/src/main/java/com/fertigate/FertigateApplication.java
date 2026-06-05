package com.fertigate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FertigateApplication {

    public static void main(String[] args) {
        SpringApplication.run(FertigateApplication.class, args);
    }
}
