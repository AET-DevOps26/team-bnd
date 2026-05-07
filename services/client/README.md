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
docker compose up -d client
# then open http://localhost:8082
```

Development
-----------

A development image is provided which runs the Vite dev server inside the container. It is useful for working in a containerised dev environment and supports live reload.

```bash
# build the dev image
docker build -f services/client/Dockerfile.dev -t team-bnd-client-dev ./services/client

# run the dev container (mount the source for live edits)
docker run -p 5173:5173 -v $(pwd)/services/client:/app -v /app/node_modules -w /app team-bnd-client-dev
# then open http://localhost:5173
```

Local (native) development
--------------------------

Alternatively, for quickest iteration on a machine with Node.js installed:

```bash
cd services/client
npm install
npm run dev
```

Notes
-----
- The dev Dockerfile starts Vite with --host 0.0.0.0 so the dev server is reachable from the host when running in a container.
- The production image serves the built files with nginx on port 80 inside the container (host port 8082 by default in docker-compose).
