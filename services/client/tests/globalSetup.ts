import fs from "fs";
import path from "path";

/**
 * Playwright global setup: obtain a token for `testuser` and
 * write the OIDC user data to a JSON file that the test fixture will inject
 * into sessionStorage.
 *
 * The app uses `oidc-client-ts` with `WebStorageStateStore` backed by
 * `sessionStorage`.  The storage key is:
 *   oidc.user:<authority>:<client_id>
 * where `authority` is derived from `window.location.origin` at runtime.
 */
export default async function globalSetup() {
  // Fetch tokens from Keycloak via the Resource Owner Password grant
  const tokenUrl =
    "http://localhost/auth/realms/alexandria/protocol/openid-connect/token";

  const body = new URLSearchParams({
    grant_type: "password",
    client_id: "alexandria-client",
    username: "testuser",
    password: "testpassword",
    scope: "openid profile",
  });

  const response = await fetch(tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });

  if (!response.ok) {
    throw new Error(
      `Keycloak token request failed: ${response.status} ${await response.text()}`,
    );
  }

  const tokens = (await response.json()) as {
    access_token: string;
    id_token: string;
    refresh_token: string;
    expires_in: number;
    token_type: string;
    scope: string;
  };

  // Decode the id_token payload to get the user profile
  const idTokenPayload = tokens.id_token.split(".")[1];
  const padded =
    idTokenPayload + "=".repeat((4 - (idTokenPayload.length % 4)) % 4);
  const profile = JSON.parse(Buffer.from(padded, "base64").toString("utf-8"));

  // Build the oidc-client-ts User object
  // The `authority` inside the running browser is http://client:8080/auth/realms/alexandria
  // because the Playwright browser navigates to http://client:8080 and the app sets:
  //   authority: `${window.location.origin}/auth/realms/alexandria`
  const authority = "http://client:8080/auth/realms/alexandria";
  const clientId = "alexandria-client";
  const storageKey = `oidc.user:${authority}:${clientId}`;

  const expiresAt = Math.floor(Date.now() / 1000) + tokens.expires_in;

  const oidcUser = {
    id_token: tokens.id_token,
    session_state: profile.sid ?? null,
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    token_type: tokens.token_type,
    scope: tokens.scope,
    profile,
    expires_at: expiresAt,
  };

  // Write the auth data file for the test fixture to consume
  const authDir = path.resolve(__dirname, ".auth");
  fs.mkdirSync(authDir, { recursive: true });

  const authData = { storageKey, oidcUser };
  const authDataPath = path.join(authDir, "user.json");
  fs.writeFileSync(authDataPath, JSON.stringify(authData, null, 2));

  console.log(`\n[globalSetup] Auth data written to ${authDataPath}`);
}
