# Alexandria (Team BND)

[![CI](https://github.com/AET-DevOps26/team-bnd/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/AET-DevOps26/team-bnd/actions/workflows/ci.yml)
[![CodeQL](https://github.com/AET-DevOps26/team-bnd/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/AET-DevOps26/team-bnd/actions/workflows/codeql.yml)
[![Azure](https://img.shields.io/badge/Live%20on%20Azure-alexandria.ddnss.de-0078D4?logo=terraform&logoColor=white)](https://alexandria.ddnss.de)
[![Kubernetes](https://img.shields.io/badge/Live%20on%20Kubernetes-alexandria.stud.k8s.aet.cit.tum.de-326CE5?logo=kubernetes&logoColor=white)](https://alexandria.stud.k8s.aet.cit.tum.de)

## Project summary

Alexandria is a document management and knowledge extraction platform. Users upload documents, e.g., research papers, reports, manuals, meeting notes, and the system automatically organizes, tags, and summarizes them.
Users get a concise summary and can ask questions about their documents, instead of having to read through a 40-page report to find what they need.

The core workflow is: Upload a document, get an auto-generated summary with extracted key entities, browse and search your knowledge base, and optionally query the GenAI for specific answers concerning your uploaded content.

## Overview

Alexandria consists of three main subsystems orchestrated via Docker Compose and Traefik:

- **Client**: A React SPA serving as the web interface.
- **Server**: Three Spring Boot microservices (user-service, knowledgebase-service, qa-service) exposing REST APIs, backed by PostgreSQL with a schema per service.
- **GenAI**: A Python/FastAPI service using LangChain to extract entities and summarize uploaded documents.

## Test coverage

| Service                      | Coverage                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Spring user-service          | ![user-service coverage](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fgist.githubusercontent.com%2FDoPri%2F81deec5c116cb700b4445f9d9fe1706a%2Fraw%2Fteam-bnd-user-service-coverage.xml&query=round%28100%20%2A%20number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40covered%29%20div%20%28number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40covered%29%20%2B%20number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40missed%29%29%29&label=coverage&suffix=%25&color=228B22)                   |
| Spring knowledgebase-service | ![knowledgebase-service coverage](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fgist.githubusercontent.com%2FDoPri%2F81deec5c116cb700b4445f9d9fe1706a%2Fraw%2Fteam-bnd-knowledgebase-service-coverage.xml&query=round%28100%20%2A%20number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40covered%29%20div%20%28number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40covered%29%20%2B%20number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40missed%29%29%29&label=coverage&suffix=%25&color=228B22) |
| Spring qa-service            | ![qa-service coverage](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fgist.githubusercontent.com%2FDoPri%2F81deec5c116cb700b4445f9d9fe1706a%2Fraw%2Fteam-bnd-qa-service-coverage.xml&query=round%28100%20%2A%20number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40covered%29%20div%20%28number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40covered%29%20%2B%20number%28%2Freport%2Fcounter%5B%40type%3D%27LINE%27%5D%2F%40missed%29%29%29&label=coverage&suffix=%25&color=228B22)                       |
| Python GenAI                 | ![genai coverage](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fgist.githubusercontent.com%2FDoPri%2F81deec5c116cb700b4445f9d9fe1706a%2Fraw%2Fteam-bnd-genai-coverage.xml&query=round%28100%20%2A%20number%28%2Fcoverage%2F%40line-rate%29%29&label=coverage&suffix=%25&color=228B22)                                                                                                                                                                                                                                    |

## Local Quickstart

Our `docker-compose.yml` includes both pre-built image references and local build contexts. You can choose to pull images for instant startup or build them locally.

**Pull and Run (Fastest):**

1. `docker compose pull && docker compose up -d`
2. Open http://localhost/ to view the site.
3. A test user is provided for quick access to the main page: username `testuser`, password `testpassword`.

**Build and Run (For Development):**
To build the images from your local source: `docker compose up --build --force-recreate`

### Environment Files

For local development, `docker compose up` works out of the box; safe defaults are embedded in `docker-compose.yml`. For production or CI, copy `.env.example` to `.env` and set the values as needed.

#### Troubleshooting

- Make sure to remove all containers _and_ docker volumes if you change to a local .env file. Otherwise, e.g., the postgres service will use the old password, leading to failed connections on the server side. This can be achieved by running `docker compose rm <container>` and `docker volume rm <volume>`.

## Setup

### API Documentation

The REST API is documented from `api/openapi.yaml`, our single source of truth. A Redoc render is published to GitHub Pages whenever the spec changes on main:

https://aet-devops26.github.io/team-bnd/

The per-service Swagger UIs are also available locally through Traefik (see the routes below).

### Traefik Reverse Proxy

All services are accessed through Traefik as the reverse proxy. See [`docs/Traefik.md`](docs/Traefik.md) for architecture, routing, and configuration details.

**Quick reference:**

| URL                                         | Service                        |
| ------------------------------------------- | ------------------------------ |
| http://localhost/                           | Client                         |
| http://localhost/api/v1/...                 | Spring API                     |
| http://localhost/user-service/docs          | user-service API docs          |
| http://localhost/knowledgebase-service/docs | knowledgebase-service API docs |
| http://localhost/qa-service/docs            | qa-service API docs            |
| http://localhost/genai/docs                 | GenAI API documentation        |
| http://localhost/auth/                      | Keycloak                       |
| http://localhost/grafana/                   | Grafana dashboards             |
| http://localhost/prometheus/                | Prometheus                     |

### Infrastructure & Deployment

- **Azure VM (Terraform + Ansible)**: Provisioning details live under [`infra/azure/README.md`](infra/azure/README.md).
- **Kubernetes (Helm)**: Cluster deployment and troubleshooting details live under [`infra/k8s/README.md`](infra/k8s/README.md).

### Git Repository

This repository uses pre-commit hooks to enforce code quality, formatting, and OpenAPI spec validity. Install [pre-commit](https://pre-commit.com/) and run `pre-commit install` after cloning.

On every `git commit`, these checks run automatically. For a full list of enforced hooks and their configurations, refer to [`.pre-commit-config.yaml`](.pre-commit-config.yaml).

To run the full hook set manually:
`pre-commit run --all-files`

### Server

Three Spring Boot microservices (user-service, knowledgebase-service, qa-service) that split the former monolith along its package boundaries, sharing one Postgres instance with a schema per service.
For local server development, see [`services/spring/README.md`](services/spring/README.md).

### Client

React SPA serving as the web client.
For local client development, see [`services/client/README.md`](services/client/README.md).

### GenAI

Python/FastAPI service using LangChain to extract entities and summarize documents.
For local Python dev, see [`services/genai/README.md`](services/genai/README.md).

### Monitoring

Prometheus scrapes metrics from each Spring service (`/actuator/prometheus`, one job per service), GenAI (`/genai/metrics`), Traefik, and the SeaweedFS object storage (`s3-storage:9091/metrics`), and Grafana visualizes them. Both run as part of `docker compose up`.

- Grafana: http://localhost/grafana/ (built-in admin login `admin` / `admin`, override with `GRAFANA_ADMIN_PASSWORD`; or "Sign in with Keycloak" using a realm user)
- Prometheus: http://localhost/prometheus/

Grafana login goes through Keycloak via OpenID Connect (generic OAuth against the `grafana` client in the `alexandria` realm). Realm users with the `grafana-admin` role land as Grafana Admins, everyone else as Viewers. The built-in admin login stays available as a fallback.

Dashboards are provisioned automatically under the "Alexandria" folder: an overview (request rate, errors, latency across services), an aggregate Spring dashboard plus one per Spring service (JVM, GC, threads, DB pool), a GenAI dashboard (request rate, latency, process memory), and an object storage dashboard (S3 request rate and latency, in-flight requests, disk usage). Dashboard JSON and the Prometheus scrape and alert config live under [`infra/prometheus`](infra/prometheus) and [`infra/grafana`](infra/grafana).

#### Tracing (opt-in)

The GenAI service can emit OpenTelemetry traces so you can see where a request spends its time, the query embedding vs. the LLM call. It's off by default; bring it up with the tracing overlay, which adds a local Jaeger and points GenAI at it:

```bash
docker compose -f docker-compose.yml -f docker-compose.tracing.yml up
```

Jaeger UI is then at http://localhost/jaeger/ (service `alexandria-genai`). Details are in [`services/genai/README.md`](services/genai/README.md#tracing).
