# API
The API is generated automatically from the annotated REST endpoints in the server code.
To regenerate the `openapi.yaml` run `./gradlew generateOpenApiDocs` in the spring `app` directory.

The respective client implementations can then be generated via tools as described in the "Best Practices Microservices" section on Artemis.

Changes to the API should lead to an updated version number, such that clients don't break on update.