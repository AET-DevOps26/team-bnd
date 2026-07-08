import { UserConfig } from "vite";

export default {
  server: {
    proxy: {
      "/api/v1/qa": "http://qa-service:8080",
      "/api/v1/knowledgebase": "http://knowledgebase-service:8080",
      "/api/v1/users": "http://user-service:8080",
      "/auth": {
        target: "http://keycloak:8180",
        xfwd: true,
      },
    },
  },
} satisfies UserConfig;
