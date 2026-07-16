import createFetchClient from "openapi-fetch";
import createQueryClient from "openapi-react-query";
import type { paths } from "./schema";
import { userManager } from "../oidcConfig";

// Empty default keeps same-origin behaviour behind Traefik. Override with
// VITE_API_URL at build time when the client is served from a different origin.
export const fetchClient = createFetchClient<paths>({
  baseUrl: import.meta.env.VITE_API_URL ?? "",
});

fetchClient.use({
  async onRequest({ request }) {
    const user = await userManager.getUser();
    if (user?.access_token) {
      request.headers.set("Authorization", `Bearer ${user.access_token}`);
    }
    return request;
  },
  async onResponse({ response }) {
    if (!response.ok) {
      if (response.status === 401) {
        await userManager
          .signinSilent()
          .then((r) => {
            if (r == null) throw new Error("NOT_AUTHENTICATED");
            return r;
          })
          .catch(() => userManager.signinRedirect())
          .catch(() => null);
        throw new Error("NOT_AUTHENTICATED");
      }
      throw new Error(
        response.status === 403
          ? "FORBIDDEN"
          : response.status === 404
            ? "NOT_FOUND"
            : `Request failed: ${response.status} ${response.statusText}`,
      );
    }
    return undefined;
  },
});

const $api = createQueryClient(fetchClient);
export default $api;
