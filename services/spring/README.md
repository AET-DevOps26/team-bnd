# Alexandria Spring Service

This directory contains the code for the spring service for the Alexandria project.

## Endpoints
Refer to [`api/openapi.yaml`](../../api/openapi.yaml) for the Spring and GenAI API endpoints documentation.

Additional endpoints:

| URL | Service |
|-----|---------|
| http://localhost/api/... | Spring API |
| http://localhost/swagger-ui/index.html | Spring API documentation |
| http://localhost/v3/api-docs| Spring API documentation |
| http://localhost/hello | Spring health check |


## Production

Build and run the production image with docker-compose:
```bash
docker compose up -d spring
# then open e.g. http://localhost/hello
```

## Local Development

If you are actively developing the spring service, you might want to rebuild the image with
your local changes instead of pulling the latest image from the repository:

```bash
docker compose up -d spring --build
# then open e.g. http://localhost/hello
```

## Testing

### Performing individual API Calls
For most API endpoints, a Bearer auth token is required, which can be requested from keycloak:
```bash
TOKEN=$(curl -s -X POST "http://localhost/auth/realms/alexandria/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=alexandria-client" \
  -d "username=<insert-username-here>" \
  -d "password=<insert-password-here>" | jq -r '.access_token')

# Then you can perform API calls using the $TOKEN shell variable, e.g.,
curl -i -H "Authorization: Bearer $TOKEN" http://localhost/api/v1/knowledgebase/documents
```
### Run all Test Cases
If you want to run the test cases for the spring service locally, you can do it as follows:
```bash
# execute in services/spring/app/
./gradlew test --no-daemon
```

The generated report can then be found at `build/reports/tests/test/index.html`.
