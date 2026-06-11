package com.fitsync.web.security;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.web.error.ApiError;
import com.fitsync.web.error.ApiErrorFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectWriter apiErrorWriter;
    private final ApiErrorFactory apiErrorFactory;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, ApiErrorFactory apiErrorFactory) {
        this.apiErrorWriter = objectMapper.writerFor(ApiError.class);
        this.apiErrorFactory = apiErrorFactory;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ApiError error = apiErrorFactory.create(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Access is denied",
                request,
                List.of());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        apiErrorWriter.writeValue(response.getOutputStream(), error);
    }
}
