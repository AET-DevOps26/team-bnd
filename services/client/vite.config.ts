import { defineConfig, type PluginOption } from "vite";

const proxy = {
  "/api/v1/qa": "http://qa-service:8080",
  "/api/v1/knowledgebase": "http://knowledgebase-service:8080",
  "/api/v1/users": "http://user-service:8080",
  "/auth": { target: "http://keycloak:8180", xfwd: true },
};

// Instrument the app for coverage only when COVERAGE=true (the e2e coverage
// build; see docker-compose.coverage.yml). vite-plugin-istanbul is ESM-only, so
// it is loaded via dynamic import to keep vite's config loader happy, and
// forceBuildInstrument makes it instrument the production `vite build` rather
// than just the dev server.
export default defineConfig(async () => {
  const plugins: PluginOption[] = [];
  if (process.env.COVERAGE === "true") {
    const istanbul = (await import("vite-plugin-istanbul")).default;
    plugins.push(
      istanbul({
        include: "src/**/*",
        exclude: [
          "src/**/*.test.*",
          "src/test/**",
          "src/**/__mocks__/**",
          "src/api/schema.d.ts",
          "src/main.tsx",
        ],
        extension: [".ts", ".tsx"],
        requireEnv: false,
        forceBuildInstrument: true,
      }),
    );
  }
  return { plugins, server: { proxy } };
});
