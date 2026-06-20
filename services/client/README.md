# Alexandria Web Client

This directory contains a minimal Vite + React client for the Alexandria project.

Production
----------

Build and run the production image (uses the multi-stage Dockerfile):

```bash
# from repository root
docker build -t team-bnd-client ./services/client
docker run -p 8082:80 team-bnd-client
```

Or with docker-compose (builds the image and serves it with nginx):

```bash
docker compose up -d client --build
# then open http://localhost/
```

Development
-----------

A development image is provided which runs the Vite dev server inside the container. It is useful for working in a containerised dev environment and supports live reload. The Spring server and Keycloak are proxied at `http://localhost:5173/api` and `http://localhost:5173/auth`.

```bash
# bring up entire stack
docker compose up -d
# build and start dev image (has to be executed again when deps are added)
docker compose --profile dev up client-dev -d --build --renew-anon-volumes
# then open http://localhost:5173
```

Testing
-------

Playwright is used for end-to-end tests. The tests run against a remote browser served by a Playwright Docker container.

Include the Playwright server when starting services:

```bash
docker compose --profile e2e up
```

Run the tests:

```bash
cd services/client
npm run test:e2e
```

Notes
-----
- The dev Dockerfile starts Vite with --host 0.0.0.0 so the dev server is reachable from the host when running in a container.
- The production image serves the built files with nginx on port 80 inside the container (host port 8082 by default in docker-compose).
- When starting the development server, `--build --renew-anon-volumes` has to be specified.
