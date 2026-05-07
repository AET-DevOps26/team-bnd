# team-bnd

## Project summary
Alexandria is a document management and knowledge extraction platform. Users upload documents, e.g., research papers, reports, manuals, meeting notes, and the system automatically organizes, tags, and summarizes them.
Users get a concise summary and can ask questions about their documents, instead of having to read through a 40-page report to find what they need.

The core workflow is: Upload a document, get an auto-generated summary with extracted key entities, browse and search your knowledge base, and optionally query the GenAI for specific answers concerning your uploaded content.

## Overview
TODO

## Setup

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

To run the full hook set manually:
`pre-commit run --all-files`

### Server
To start up the spring-boot service, a Dockerfile is provided. To use it:
1. Navigate to `services/spring/`
2. Build the image: `docker build -t spring`
3. Run the container: `docker run -p 8080:8080 spring`
4. Perform API calls: e.g. `curl http://localhost:8080/hello`
5. Enjoy!

Alternatively, you can just use docker compose: `docker compose up -d`. To force a fresh gradle build, run `docker compose up --build --force-recreate --no-deps`.

### Client
Client assets live under `services/client/` and are served as a static frontend.

Quickest way:
1. `docker compose up --build client`
2. Open http://localhost:8082 or run `curl http://localhost:8082` to verify the site is up.

To force a fresh build: `docker compose up --build --force-recreate --no-deps client`.

For local frontend development (hot-reload, tests, linting), see `services/client/README.md`.

Troubleshooting:
- If the port 8082 is already in use, stop the conflicting process or change the port mapping in `docker-compose.yml`.
- If assets don't update, rebuild the image or clear the browser cache.

Health check example: `curl -I http://localhost:8082/` should return HTTP 200.

### GenAI
Python/FastAPI under `services/genai/`.

Quickest way:
1. `docker compose up --build genai`
2. `curl http://localhost:8000/genai/hello`

For local Python dev (tests, autoreload), see [`services/genai/README.md`](services/genai/README.md).
