package com.fitsync.gateway.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public interface GatewayProxyService {

    ResponseEntity<byte[]> forwardToAuthService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToUserService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToWorkoutService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToNutritionService(HttpServletRequest request, byte[] body);

    ResponseEntity<byte[]> forwardToAnalyticsService(HttpServletRequest request, byte[] body);
}
