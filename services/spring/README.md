# Alexandria Spring Microservices

## Architecture
The Spring backend is split into three microservices that live as Gradle sub-modules of a single multi-project build:

| Module                                            | Ports                                            | Database tables                                                                            | Purpose                                                                                    |
|---------------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| [`user-service/`](./user-service/)                | 8080 (mapped by Traefik under `/api/v1/users`)   | `user.users`                                                                               | Account management and OIDC handling                                                       |
| [`knowledgebase-service/`](./knowledgebase-service/) | 8080 (mapped by Traefik under `/api/v1/knowledgebase`) | `knowledgebase.{documents,summaries,tags,document_tags,extracted_entities,search_queries}` | Uploads, tagging, text search, GenAI summarization and entity extraction                   |
| [`qa-service/`](./qa-service/)                    | 8080 (mapped by Traefik under `/api/v1/qa`)      | `qa.{qa_interactions,qa_source_documents}`                                                 | Question answering, delegated to knowledgebase-service for document keys and GenAI service |

All three services share one Postgres instance but use schema-per-service, as given in the Microservices Best Practices.
Cross-service ownership fields (`Document.ownerSubject`, `SearchQuery.userSubject`,
`QAInteraction.userSubject`) store the caller's OIDC subject as a plain string
instead of a foreign key, so no service has to read another service's schema.

## Endpoints

The public API is specified in [`api/openapi.yaml`](../../api/openapi.yaml).
Each service exposes its own Swagger UI, routed through Traefik:

| URL                                          | Service                        |
|----------------------------------------------|--------------------------------|
| /api/v1/users/...                            | user-service API               |
| /user-service/swagger-ui.html                | user-service API docs          |
| /api/v1/knowledgebase/…                      | knowledgebase-service API      |
| /knowledgebase-service/swagger-ui.html       | knowledgebase-service API docs |
| /api/v1/qa/…                                 | qa-service API                 |
| /qa-service/swagger-ui.html                  | qa-service API docs            |

The three services also expose a set of `/internal/{knowledgebase,qa}/**` endpoints for service-to-service calls. This prefix is deliberately not routed by Traefik or the Kubernetes Ingress, so the endpoints are only reachable from other containers inside the `alexandria` network. They are described in the `info` section of the aggregated OpenAPI spec.

## Production

The three services share one Dockerfile, the concrete image is picked at build
time via `--build-arg SERVICE=<name>`. `docker-compose.yml` automatically sets these arguments.

```bash
docker compose up -d user-service knowledgebase-service qa-service
# then open e.g. http://localhost/knowledgebase-service/swagger-ui.html
```

## Local Development

If you are actively developing one of the services and want to rebuild against
your local changes instead of pulling the latest image:

```bash
docker compose up -d user-service knowledgebase-service qa-service --build
```

You can also build a single service in isolation:

```bash
# executed in services/spring/
./gradlew :knowledgebase-service:bootRun
```

## Testing

### Performing individual API calls

For most endpoints a Bearer auth token is required, which can be requested from
Keycloak:

```bash
TOKEN=$(curl -s -X POST "http://localhost/auth/realms/alexandria/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=alexandria-client" \
  -d "username=<insert-username-here>" \
  -d "password=<insert-password-here>" | jq -r '.access_token')

# List documents (knowledgebase-service)
curl -i -H "Authorization: Bearer $TOKEN" http://localhost/api/v1/knowledgebase/documents

# Ask a question (qa-service)
curl -i -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"question":"What is Alexandria?"}' \
     http://localhost/api/v1/qa/ask
```

### Run all test cases

Tests are given inside each Gradle sub-module. From `services/spring/` you can run
one service or all of them:

```bash
# one service
./gradlew :user-service:test --no-daemon

# all services
./gradlew test --no-daemon
```

Test reports for a given service are written to
`services/spring/<service>/build/reports/tests/test/index.html`.
