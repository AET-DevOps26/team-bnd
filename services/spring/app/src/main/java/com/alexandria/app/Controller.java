package com.alexandria.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Tag(name = "Health", description = "Liveness and smoke endpoints")
public class Controller {
    @GetMapping(value = "/hello", produces = "text/plain")
    @Operation(summary = "Health check", description = "Returns 'Hello World!' if service is running")
    public String helloEndpoint() {
        return "Hello World!";
    }
}
