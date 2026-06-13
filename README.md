# team-bnd

## Project summary

Alexandria is a document management and knowledge extraction platform. Users upload documents, e.g., research papers, reports, manuals, meeting notes, and the system automatically organizes, tags, and summarizes them.
Users get a concise summary and can ask questions about their documents, instead of having to read through a 40-page report to find what they need.

The core workflow is: Upload a document, get an auto-generated summary with extracted key entities, browse and search your knowledge base, and optionally query the GenAI for specific answers concerning your uploaded content.

## Overview

Alexandria consists of three main subsystems orchestrated via Docker Compose and Traefik:

- **Client**: A React SPA serving as the web interface.
- **Server**: A Spring Boot application handling core logic, user management, and the PostgreSQL database.
- **GenAI**: A Python/FastAPI service using LangChain to extract entities and summarize uploaded documents.

## Setup


### Traefik Reverse Proxy

All services are accessed through Traefik as the reverse proxy. See [`docs/traefik.md`](docs/traefik.md) for architecture, routing, and configuration details.

**Quick reference:**
| URL | Service |
|-----|---------|
| http://localhost/ | Client |
| http://localhost/api/v1/... | Spring API |
| http://localhost/swagger-ui/ | Spring API documentation |
| http://localhost/genai/docs | GenAI API documentation |
| http://localhost/auth/ | Keycloak |

### Infrastructure & Deployment

- **Azure VM (Terraform + Ansible)**: Provisioning details live under [`infra/azure/README.md`](infra/azure/README.md).
- **Kubernetes (Helm)**: Cluster deployment and troubleshooting details live under [`infra/k8s/README.md`](infra/k8s/README.md).

### Git Repository

This repository uses pre-commit hooks to enforce code quality, formatting, and OpenAPI spec validity. Install [pre-commit](https://pre-commit.com/) and run `pre-commit install` after cloning.

On every `git commit`, these checks run automatically. For a full list of enforced hooks and their configurations, refer to [`.pre-commit-config.yaml`](.pre-commit-config.yaml).

To run the full hook set manually:
`pre-commit run --all-files`

### Local Quickstart

Our `docker-compose.yml` includes both pre-built image references and local build contexts. You can choose to pull images for instant startup or build them locally.

**Pull and Run (Fastest):**
1. `docker compose pull && docker compose up -d`
2. Open http://localhost/ to view the site.

**Build and Run (For Development):**
To build the images from your local source: `docker compose up --build --force-recreate`

### Environment Files

The project uses a two-file pattern for configuration:

| File | Committed | Purpose |
|------|-----------|---------|
| `.env.config` | Yes | Non-secret configuration (API URLs, feature flags) |
| `.env` | No | Secrets (passwords, API keys), copy from `.env.example` |

For local development, `docker compose up` works out of the box; safe defaults are embedded in `docker-compose.yml`. For production or CI, set environment variables explicitly or create a `.env` file from `.env.example`.

### Server

Spring Boot application handling the core server logic and database.
For local server development, see [`services/spring/README.md`](services/spring/README.md).

### Client

React SPA serving as the web client.
For local client development, see [`services/client/README.md`](services/client/README.md).

### GenAI

Python/FastAPI service using LangChain to extract entities and summarize documents.
For local Python dev, see [`services/genai/README.md`](services/genai/README.md).
