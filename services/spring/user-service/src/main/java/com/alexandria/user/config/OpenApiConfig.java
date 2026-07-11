package com.alexandria.user.config;

import com.alexandria.user.exception.GlobalExceptionHandler;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Alexandria User Service API", version = "2.3.0", description = "User account management. Owns the users table; exposes internal fan-out endpoints consumed by knowledgebase-service and qa-service.", license = @License(name = "MIT", identifier = "MIT")
        ), servers = @Server(url = "/", description = "Current server")
)
@SecurityScheme(
        name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT"
)
public class OpenApiConfig {

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    // Springdoc infers each error response from the handler method's return type, which
    // leaves 4xx/5xx pointing at the success DTO. Rewrite every non-2xx response to the
    // shared ErrorResponse schema so the spec matches what GlobalExceptionHandler returns.
    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            if (openApi.getComponents() != null) {
                ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(GlobalExceptionHandler.ErrorResponse.class)).referencedSchemas.forEach(openApi.getComponents()::addSchemas);
            }
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }
                operation.getResponses().forEach((status, response) -> {
                    if (isErrorStatus(status)) {
                        response.setContent(errorContent());
                    }
                });
            }));
        };
    }

    private static Content errorContent() {
        return new Content().addMediaType(
                "application/json", new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));
    }

    private static boolean isErrorStatus(String status) {
        try {
            return Integer.parseInt(status) >= 400;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
