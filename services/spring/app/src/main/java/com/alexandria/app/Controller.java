package com.alexandria.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class Controller {
    @GetMapping(value = "/hello", produces = "text/plain")
    public String helloEndpoint() {
        return "Hello World!";
    }
}
