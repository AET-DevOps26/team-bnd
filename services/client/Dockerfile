# Multi-stage Dockerfile for building and serving the Vite app
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json ./
COPY package-lock.json* ./
RUN npm ci --silent || npm install --no-audit --no-fund --silent
COPY . .
RUN npm run build

FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:80/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
