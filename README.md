# team-bnd

## Project summary
Alexandria is a document management and knowledge extraction platform. Users upload documents, e.g., research papers, reports, manuals, meeting notes, and the system automatically organizes, tags, and summarizes them.
Users get a concise summary and can ask questions about their documents, instead of having to read through a 40-page report to find what they need.

The core workflow is: Upload a document, get an auto-generated summary with extracted key entities, browse and search your knowledge base, and optionally query the GenAI for specific answers concerning your uploaded content.

## Overview
TODO

## Setup

### Traefik Reverse Proxy

All services are accessed through Traefik as the reverse proxy. See [`docs/traefik.md`](docs/traefik.md) for architecture, routing, and configuration details.

**Quick reference:**
| URL | Service |
|-----|---------|
| http://localhost/ | Client (frontend) |
| http://localhost/api/v1/... | Spring API |
| http://localhost/swagger-ui/ | API documentation |
| http://localhost/auth/ | Keycloak |

### Infrastructure (Terraform + Ansible)

Provisioning for Azure VM deployment lives under [`infra/azure/`](infra/azure/README.md).

### Git Repository
This repository uses pre-commit hooks. Install [pre-commit](https://pre-commit.com/) and run `pre-commit install` after cloning.

On every `git commit`, these checks run automatically:
- `trailing-whitespace`
- `end-of-file-fixer`
- `check-yaml` (except GitHub workflow files)
- `check-added-large-files` (max 1024 KB)
- `check-merge-conflict`
- `mixed-line-ending`
- `yamllint` with `.yamllint.yaml`
- `hadolint-docker` with `.hadolint.yaml`
- `actionlint`
- `redocly-lint` via `npx --yes @redocly/cli@2.30.3 lint api/openapi.yaml` for files under `api/`
- `redocly-join` via `npx --yes @redocly/cli@2.30.3 join api/spring-openapi.yaml api/genai-openapi.yaml -o api/openapi.yaml` to merge generated OpenAPI specs into a unified YAML.

To run the full hook set manually:
`pre-commit run --all-files`

### Server
Spring Boot lives under `services/spring/`.
Quickest way:
1. `docker compose up -d spring`
2. Perform API calls, e.g., `curl http://localhost/hello`

To force a fresh gradle build, run `docker compose up --build --force-recreate --no-deps spring`.

### Client
Client assets live under `services/client/` and are served as a static frontend.

Quickest way:
1. `docker compose up -d client`
2. Open http://localhost/ to view the site.

To force a fresh build: `docker compose up --build --force-recreate --no-deps client`.

For local frontend development (hot-reload, tests, linting), see `services/client/README.md`.

### GenAI
Python/FastAPI under `services/genai/`.

Quickest way:
1. `docker compose up -d genai`
2. `curl http://localhost/genai/hello`

For local Python dev (tests, autoreload), see [`services/genai/README.md`](services/genai/README.md).

### Kubernetes Deployment

A Helm chart is provided in `infra/k8s/alexandria/`.

Prerequisites:
- A running Kubernetes cluster (Rancher, Azure AKS, minikube, etc.)
- `helm` and `kubectl` configured to access the cluster

Secrets Setup:
1. Copy the secrets template:
   ```bash
   cp infra/k8s/alexandria/values-secrets.yaml.example infra/k8s/alexandria/values-secrets.yaml
   ```
2. Edit `values-secrets.yaml` and set your actual passwords (this file is gitignored)

Deploy:
```bash
helm install alexandria infra/k8s/alexandria \
  --namespace alexandria --create-namespace \
  --dependency-update \
  -f infra/k8s/alexandria/values-secrets.yaml
```

Upgrade after changes:
```bash
helm upgrade alexandria infra/k8s/alexandria \
  --namespace alexandria \
  --dependency-update \
  -f infra/k8s/alexandria/values-secrets.yaml
```

Uninstall:
```bash
helm uninstall alexandria --namespace alexandria
```

Override values (e.g., different image tag):
```bash
helm install alexandria infra/k8s/alexandria \
  --namespace alexandria --create-namespace \
  --dependency-update \
  -f infra/k8s/alexandria/values-secrets.yaml \
  --set image.tag=sha-abc123 \
  --set ingress.host=alexandria.example.com
```

Check status:
```bash
kubectl -n alexandria get pods
kubectl -n alexandria get svc
kubectl -n alexandria get ingress
```

### Kubernetes Troubleshooting

**Spring or Keycloak fail postgres password authentication after a reinstall**

The postgres subchart creates a PersistentVolumeClaim that survives `helm uninstall`. On a subsequent install, postgres reuses the existing data directory (with the old password) and ignores the new `POSTGRES_PASSWORD` value, causing spring and keycloak to fail authentication.

Fix: delete the PVC before reinstalling.

```bash
helm uninstall alexandria --namespace alexandria
kubectl -n alexandria delete pvc --all
helm install alexandria infra/k8s/alexandria \
  --namespace alexandria --create-namespace \
  --dependency-update \
  -f infra/k8s/alexandria/values-secrets.yaml
```
