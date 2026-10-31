package com.liftcare.m4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiftCareM4Application {
    public static void main(String[] args) { SpringApplication.run(LiftCareM4Application.class, args); }
}
