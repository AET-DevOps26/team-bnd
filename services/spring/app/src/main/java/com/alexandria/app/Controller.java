package com.alexandria.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Tag(name = "Health", description = "Liveness and smoke endpoints")
public class Controller {
    @GetMapping(value = "/hello", produces = "text/plain")
    @Operation(summary = "Health check", description = "Returns 'Hello World!' if service is running")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Service is running")
    @ApiResponse(responseCode = "404", description = "Endpoint not found")
    public String helloEndpoint() {
        return "Hello World!";
    }
}
