import { describe, it, expect } from "vitest";
import { entityKey } from "./MainView";

describe("entityKey", () => {
  it("joins type and name with a null separator", () => {
    expect(entityKey("PERSON", "Ada Lovelace")).toBe(
      "PERSON\u0000Ada Lovelace",
    );
  });

  it("keeps identically named entities of different types distinct", () => {
    expect(entityKey("PERSON", "May")).not.toBe(entityKey("DATE", "May"));
  });
});
