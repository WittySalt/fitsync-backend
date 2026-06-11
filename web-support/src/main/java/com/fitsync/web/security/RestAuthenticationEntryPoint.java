package com.fitsync.web.security;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.web.error.ApiError;
import com.fitsync.web.error.ApiErrorFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final ApiErrorFactory apiErrorFactory;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, ApiErrorFactory apiErrorFactory) {
        this.objectMapper = objectMapper;
        this.apiErrorFactory = apiErrorFactory;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        ApiError error = apiErrorFactory.create(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required to access this resource",
                request,
                List.of());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
