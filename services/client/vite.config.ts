import { UserConfig } from "vite";

export default {
  server: {
    proxy: {
      "/api": "http://spring:8080",
      "/auth": {
        target: "http://keycloak:8180",
        xfwd: true,
      },
    },
  },
} satisfies UserConfig;
