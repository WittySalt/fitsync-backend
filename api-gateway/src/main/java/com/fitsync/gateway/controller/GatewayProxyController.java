package com.fitsync.gateway.controller;

import com.fitsync.gateway.service.GatewayProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayProxyController {

    private final GatewayProxyService gatewayProxyService;

    public GatewayProxyController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    @RequestMapping(path = "/api/v1/auth/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyAuth(HttpServletRequest request,
                                            @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToAuthService(request, body);
    }

    @RequestMapping(path = "/api/v1/users/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyUser(HttpServletRequest request,
                                            @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToUserService(request, body);
    }

    @RequestMapping(path = "/api/v1/workouts/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyWorkout(HttpServletRequest request,
                                               @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToWorkoutService(request, body);
    }

    @RequestMapping(path = "/api/v1/nutrition/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyNutrition(HttpServletRequest request,
                                                 @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToNutritionService(request, body);
    }

    @RequestMapping(path = "/api/v1/analytics/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyAnalytics(HttpServletRequest request,
                                                 @RequestBody(required = false) byte[] body) {
        return gatewayProxyService.forwardToAnalyticsService(request, body);
    }
}
