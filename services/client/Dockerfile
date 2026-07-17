# syntax=docker/dockerfile:1

# Multi-stage Dockerfile for building and serving the Vite app
FROM node:24-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS build
# COVERAGE=true instruments the build with istanbul for the e2e coverage run.
ARG COVERAGE=false
ENV COVERAGE=$COVERAGE
WORKDIR /app
COPY package.json ./
COPY package-lock.json* ./
RUN npm ci
COPY . .
RUN npm run build


FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:b3f2436575bd5be7386518084d842dac414ab4962712afa31e99e0942a56e3b2
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
