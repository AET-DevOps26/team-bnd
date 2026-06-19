import { AuthProviderProps } from "react-oidc-context";

export const oidcConfig: AuthProviderProps = {
  authority: `${window.location.origin}/auth/realms/alexandria`,
  client_id: "alexandria-client",
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  scope: "openid profile",
  onSigninCallback: () => {
    // Remove OIDC query params from URL after successful login
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
