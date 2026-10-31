package com.liftcare.m4.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${liftcare.cors-origin}") String origin;
    @Override public void addCorsMappings(CorsRegistry r) {
        r.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET", "POST", "PUT", "DELETE").allowedHeaders("*");
    }
}
