import { describe, it, expect, vi, beforeAll, beforeEach, afterAll } from "vitest";

const userManagerMock = vi.hoisted(() => ({
  getUser: vi.fn(),
  signinSilent: vi.fn(),
  signinRedirect: vi.fn(),
}));
vi.mock("../oidcConfig", () => ({ userManager: userManagerMock }));

// openapi-fetch captures globalThis.fetch when the client is created, so the
// stub has to be in place before ./client is imported. Hence the dynamic import.
const fetchSpy = vi.hoisted(() => vi.fn());

let fetchClient: typeof import("./client").fetchClient;
let $api: typeof import("./client").default;

const TAGS = "/api/v1/knowledgebase/tags";

function jsonResponse(status: number, body: unknown = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

beforeAll(async () => {
  vi.stubGlobal("fetch", fetchSpy);
  const mod = await import("./client");
  fetchClient = mod.fetchClient;
  $api = mod.default;
});

afterAll(() => {
  vi.unstubAllGlobals();
});

beforeEach(() => {
  fetchSpy.mockReset();
  userManagerMock.getUser.mockReset();
  userManagerMock.signinSilent.mockReset();
  userManagerMock.signinRedirect.mockReset();
});

describe("api client", () => {
  it("exposes the react-query wrapper as the default export", () => {
    expect($api).toBeDefined();
    expect(typeof $api.useQuery).toBe("function");
  });

  it("attaches a bearer token when a user is signed in", async () => {
    userManagerMock.getUser.mockResolvedValue({ access_token: "tok-123" });
    fetchSpy.mockResolvedValue(jsonResponse(200, { tags: [] }));

    await fetchClient.GET(TAGS);

    const request = fetchSpy.mock.calls[0]?.[0] as Request;
    expect(request.headers.get("Authorization")).toBe("Bearer tok-123");
  });

  it("sends no auth header when nobody is signed in", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    fetchSpy.mockResolvedValue(jsonResponse(200, { tags: [] }));

    await fetchClient.GET(TAGS);

    const request = fetchSpy.mock.calls[0]?.[0] as Request;
    expect(request.headers.get("Authorization")).toBeNull();
  });

  it("maps 403 to FORBIDDEN", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    fetchSpy.mockResolvedValue(jsonResponse(403));
    await expect(fetchClient.GET(TAGS)).rejects.toThrow("FORBIDDEN");
  });

  it("maps 404 to NOT_FOUND", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    fetchSpy.mockResolvedValue(jsonResponse(404));
    await expect(fetchClient.GET(TAGS)).rejects.toThrow("NOT_FOUND");
  });

  it("maps any other failure to a status message", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    fetchSpy.mockResolvedValue(
      new Response("", { status: 500, statusText: "Server Error" }),
    );
    await expect(fetchClient.GET(TAGS)).rejects.toThrow(/Request failed: 500/);
  });

  it("tries a silent sign-in on 401 and reports NOT_AUTHENTICATED", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    userManagerMock.signinSilent.mockResolvedValue(null);
    fetchSpy.mockResolvedValue(jsonResponse(401));

    await expect(fetchClient.GET(TAGS)).rejects.toThrow("NOT_AUTHENTICATED");
    expect(userManagerMock.signinSilent).toHaveBeenCalled();
  });

  it("falls back to a redirect when silent sign-in fails", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    userManagerMock.signinSilent.mockRejectedValue(new Error("nope"));
    userManagerMock.signinRedirect.mockResolvedValue(undefined);
    fetchSpy.mockResolvedValue(jsonResponse(401));

    await expect(fetchClient.GET(TAGS)).rejects.toThrow("NOT_AUTHENTICATED");
    expect(userManagerMock.signinRedirect).toHaveBeenCalled();
  });

  it("still reports NOT_AUTHENTICATED when silent sign-in returns a user", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    userManagerMock.signinSilent.mockResolvedValue({ access_token: "x" });
    fetchSpy.mockResolvedValue(jsonResponse(401));
    await expect(fetchClient.GET(TAGS)).rejects.toThrow("NOT_AUTHENTICATED");
  });

  it("swallows a failed redirect after a failed silent sign-in", async () => {
    userManagerMock.getUser.mockResolvedValue(null);
    userManagerMock.signinSilent.mockRejectedValue(new Error("a"));
    userManagerMock.signinRedirect.mockRejectedValue(new Error("b"));
    fetchSpy.mockResolvedValue(jsonResponse(401));
    await expect(fetchClient.GET(TAGS)).rejects.toThrow("NOT_AUTHENTICATED");
    expect(userManagerMock.signinRedirect).toHaveBeenCalled();
  });
});
