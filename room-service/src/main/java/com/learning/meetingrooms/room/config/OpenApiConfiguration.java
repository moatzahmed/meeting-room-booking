package com.learning.meetingrooms.room.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI roomServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Room Service API")
                .description("Manages meeting rooms and their active or inactive status")
                .version("v1"));
    }
}
