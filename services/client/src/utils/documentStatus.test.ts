import { describe, it, expect } from "vitest";
import { isProcessing, pollWhileProcessing } from "./documentStatus";
import type { components } from "../api/schema";

type Document = components["schemas"]["Document"];

function makeDoc(overrides: Partial<Document> = {}): Document {
  return { id: "d1", fileName: "report.pdf", ...overrides };
}

describe("isProcessing", () => {
  it("is true while the summary is pending", () => {
    expect(isProcessing(makeDoc({ summary: { status: "PENDING" } }))).toBe(true);
  });

  it("is true while entity extraction is pending", () => {
    expect(isProcessing(makeDoc({ entitiesStatus: "PENDING" }))).toBe(true);
  });

  it("is true while tagging is pending", () => {
    expect(isProcessing(makeDoc({ tagsStatus: "PENDING" }))).toBe(true);
  });

  it("is false once every step has completed", () => {
    expect(
      isProcessing(
        makeDoc({
          summary: { status: "COMPLETED" },
          entitiesStatus: "COMPLETED",
          tagsStatus: "COMPLETED",
        }),
      ),
    ).toBe(false);
  });

  it("is false when no statuses are present", () => {
    expect(isProcessing(makeDoc())).toBe(false);
  });
});

describe("pollWhileProcessing", () => {
  it("stops polling when nothing is pending", () => {
    expect(pollWhileProcessing(false, 0)).toBe(false);
    expect(pollWhileProcessing(false, 250)).toBe(false);
  });

  it("polls at the fixed interval while pending and under the cap", () => {
    expect(pollWhileProcessing(true, 0)).toBe(3000);
    expect(pollWhileProcessing(true, 99)).toBe(3000);
  });

  it("gives up once the poll cap is reached", () => {
    expect(pollWhileProcessing(true, 100)).toBe(false);
    expect(pollWhileProcessing(true, 101)).toBe(false);
  });
});
