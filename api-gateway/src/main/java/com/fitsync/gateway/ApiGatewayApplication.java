package com.fitsync.gateway;

import com.fitsync.gateway.config.CognitoProperties;
import com.fitsync.gateway.config.GatewayCorsProperties;
import com.fitsync.gateway.config.GatewayRateLimitProperties;
import com.fitsync.gateway.config.GatewayRoutesProperties;
import com.fitsync.gateway.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        CognitoProperties.class,
        GatewayCorsProperties.class,
        GatewayRateLimitProperties.class,
        GatewayRoutesProperties.class
})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
