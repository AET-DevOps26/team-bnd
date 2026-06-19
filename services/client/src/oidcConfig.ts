import { AuthProviderProps } from "react-oidc-context";
import { UserManager, WebStorageStateStore } from "oidc-client-ts";

const oidcSettings = {
  authority: `${window.location.origin}/auth/realms/alexandria`,
  client_id: "alexandria-client",
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  prompt: "login",
  scope: "openid profile",
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
};

export const userManager = new UserManager(oidcSettings);

export const oidcConfig: AuthProviderProps = {
  userManager,
  onSigninCallback: () => {
    // Remove OIDC query params from URL after successful login
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
