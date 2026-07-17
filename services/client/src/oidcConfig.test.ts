import { describe, it, expect } from "vitest";
import { userManager, oidcConfig } from "./oidcConfig";

describe("oidcConfig", () => {
  it("builds a user manager wired into the auth config", () => {
    expect(userManager).toBeDefined();
    expect(oidcConfig.userManager).toBe(userManager);
  });
});
