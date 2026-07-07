package com.alexandria.qa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "QA Service")
public class HelloController {
    @GetMapping(path = "/qa-service/hello", produces = "text/plain")
    @Operation(operationId = "helloQa", summary = "Health check (qa-service)", description = "Returns 'Hello from qa-service!' if service is running")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Service is running")
    public String hello() {
        return "Hello from qa-service!";
    }
}
