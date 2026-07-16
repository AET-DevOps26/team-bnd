# Traefik Reverse Proxy

Alexandria uses [Traefik](https://traefik.io/) as the reverse proxy and API gateway for all HTTP traffic in Docker Compose deployments.

## Architecture

```
                         ┌─────────────┐
            HTTP:80 ──▶ │   Traefik   │
                         └──────┬──────┘
                                │
           ┌────────────────────┼──────────────────┐
           │                    │                  │
           ▼                    ▼                  ▼
     ┌──────────┐        ┌──────────┐        ┌──────────┐
     │  Client  │        │  Spring  │        │ Keycloak │
     │  (nginx) │        │  (API)   │        │  (OIDC)  │
     └──────────┘        └────┬─────┘        └──────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
        ┌──────────┐                   ┌──────────┐
        │  GenAI   │                   │ Postgres │
        │ (Python) │                   │   (DB)   │
        └──────────┘                   └──────────┘
```

**Public services** (accessible via Traefik):

- Client (frontend)
- Spring (REST API)
- Keycloak (OIDC provider)
- GenAI (health and docs endpoints only)
- Grafana and Prometheus (monitoring)

**Private services** (internal Docker network only):

- PostgreSQL
- Internal Spring endpoints
- GenAI (except health and docs endpoints)

Traefik also exposes its own Prometheus metrics on a separate internal `metrics` entrypoint (`:8082`), which is only reachable inside the Docker network and is scraped by Prometheus. It is not bound to the public `web` entrypoint.

## Routing Table

| Path                             | Service                          | Internal Port | Description                                  |
| -------------------------------- | -------------------------------- | ------------- | -------------------------------------------- |
| `/`                              | Client                           | 80            | Static frontend (catch-all, lowest priority) |
| `/api/v1/*`                      | Spring (user, knowledgebase, qa) | 8080          | REST API                                     |
| `/<service>-service/docs`        | Spring                           | 8080          | Per-service API documentation UI             |
| `/<service>-service/v3/api-docs` | Spring                           | 8080          | Per-service OpenAPI spec                     |
| `/auth/*`                        | Keycloak                         | 8180          | OIDC provider                                |
| `/genai/docs`                    | GenAI                            | 8000          | FastAPI documentation UI                     |
| `/genai/redoc`                   | GenAI                            | 8000          | FastAPI documentation UI (ReDoc)             |
| `/genai/openapi.json`            | GenAI                            | 8000          | FastAPI OpenAPI spec                         |
| `/grafana/*`                     | Grafana                          | 3000          | Monitoring dashboards                        |
| `/prometheus/*`                  | Prometheus                       | 9090          | Metrics store and query UI                   |

## Local Development

### Start the Stack

```bash
docker compose up -d
```

### Access Services

| URL                                         | Service                                 |
| ------------------------------------------- | --------------------------------------- |
| http://localhost/                           | Client (frontend)                       |
| http://localhost/api/...                    | Spring API                              |
| http://localhost/user-service/docs          | user-service API documentation          |
| http://localhost/knowledgebase-service/docs | knowledgebase-service API documentation |
| http://localhost/qa-service/docs            | qa-service API documentation            |
| http://localhost/auth/                      | Keycloak admin                          |
| http://localhost/genai/docs                 | GenAI API documentation                 |
| http://localhost/genai/redoc                | GenAI API documentation (ReDoc)         |
| http://localhost/genai/openapi.json         | GenAI OpenAPI spec                      |
| http://localhost/grafana/                   | Grafana dashboards                      |
| http://localhost/prometheus/                | Prometheus                              |

### Access Traefik Dashboard

The dashboard is available at `http://traefik.localhost/` when running locally.

> Note: You may need to add `127.0.0.1 traefik.localhost` to `/etc/hosts` if your browser does not automatically resolve `.localhost` domains.

## Adding New Services

To expose a new service through Traefik, add labels to the service in `docker-compose.yml`:

```yaml
my-new-service:
  image: myimage:latest
  networks:
    - alexandria
  labels:
    - "traefik.enable=true"
    - "traefik.http.routers.myservice.rule=PathPrefix(`/myservice`)"
    - "traefik.http.routers.myservice.entrypoints=web"
    - "traefik.http.routers.myservice.priority=10"
    - "traefik.http.services.myservice.loadbalancer.server.port=3000"
```

Key points:

- `traefik.enable=true` - Opt-in to Traefik routing (required since `exposedbydefault=false`)
- `priority` - Higher values take precedence; client uses `1` as catch-all
- `loadbalancer.server.port` - The container's internal port

## Troubleshooting

### Check Traefik Logs

```bash
docker compose logs traefik
```

### Verify Service Registration

Open the Traefik dashboard at `http://traefik.localhost/` and check:

- **Routers**: Shows all configured routes
- **Services**: Shows backend services and their health

### Common Issues

**Service not accessible:**

1. Check if the service has `traefik.enable=true` label
2. Verify the service is on the `alexandria` network
3. Check the port in `loadbalancer.server.port` matches the container's exposed port

**Path conflicts:**

- Use `priority` to resolve conflicts (higher = matched first)
- More specific paths should have higher priority than catch-all routes

**502 Bad Gateway:**

- The backend service is not running or not healthy
- Check `docker compose ps` and `docker compose logs <service>`

### Test Routing Manually

```bash
# Test client (should return HTML)
curl -s http://localhost/ | head -5

# Test Spring API (Swagger UI is public, most /api endpoints require a JWT)
curl -sI http://localhost/knowledgebase-service/docs

# Test Keycloak
curl http://localhost/auth/

# Test GenAI docs
curl http://localhost/genai/docs
```

## Kubernetes

For Kubernetes deployments, the Helm chart uses nginx-ingress instead of Traefik. See [`infra/k8s/alexandria/templates/ingress.yaml`](../infra/k8s/alexandria/templates/ingress.yaml) for the ingress configuration.
