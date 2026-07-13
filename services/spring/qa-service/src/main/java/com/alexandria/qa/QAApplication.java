package com.alexandria.qa;

import com.alexandria.common.internal.InternalAuthConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(InternalAuthConfig.class)
public class QAApplication {
    public static void main(String[] args) {
        SpringApplication.run(QAApplication.class, args);
    }
}
