import createFetchClient from "openapi-fetch";
import createQueryClient from "openapi-react-query";
import type { paths } from "./schema";
import { userManager } from "../oidcConfig";

export const fetchClient = createFetchClient<paths>({ baseUrl: "" });

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
      throw new Error(
        response.status === 401 || response.status === 403
          ? "NOT_AUTHENTICATED"
          : `Request failed: ${response.status} ${response.statusText}`,
      );
    }
    return undefined;
  },
});

const $api = createQueryClient(fetchClient);
export default $api;
