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
CMD ["nginx", "-g", "daemon off;"]
