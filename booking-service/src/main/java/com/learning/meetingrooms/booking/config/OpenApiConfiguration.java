package com.learning.meetingrooms.booking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI bookingServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Booking Service API")
                .description("Creates, retrieves, and cancels owned meeting-room bookings")
                .version("v1"));
    }
}
