package com.alexandria.knowledgebase.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Alexandria KnowledgeBase Service API",
                version = "2.0.0",
                description = "Document management, tagging, and text search. Owns the documents/tags/search-queries tables and calls the GenAI service for summarization and entity extraction.",
                license = @License(name = "MIT", identifier = "MIT")
        ),
        servers = @Server(url = "/", description = "Current server")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
