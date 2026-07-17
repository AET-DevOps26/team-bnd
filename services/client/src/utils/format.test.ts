import { describe, it, expect, vi, afterEach } from "vitest";
import { formatDateTime, formatDate, formatBytes } from "./format";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("formatBytes", () => {
  it("returns the fallback when the byte count is nullish", () => {
    expect(formatBytes(undefined)).toBe("");
    expect(formatBytes(undefined, "n/a")).toBe("n/a");
  });

  it("formats counts below 1 KB as plain bytes", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(1023)).toBe("1023 B");
  });

  it("formats kilobytes with one decimal from 1 KB up", () => {
    expect(formatBytes(1024)).toBe("1.0 KB");
    expect(formatBytes(1536)).toBe("1.5 KB");
    // Just under 1 MB still rounds to KB.
    expect(formatBytes(1024 * 1024 - 1)).toBe("1024.0 KB");
  });

  it("formats megabytes with one decimal from 1 MB up", () => {
    expect(formatBytes(1024 * 1024)).toBe("1.0 MB");
    expect(formatBytes(5 * 1024 * 1024)).toBe("5.0 MB");
  });
});

describe("formatDate", () => {
  it("returns the fallback for missing input", () => {
    expect(formatDate(undefined)).toBe("");
    expect(formatDate("")).toBe("");
    expect(formatDate("", "-")).toBe("-");
  });

  it("requests a date-only format and returns its result", () => {
    // Assert the requested options (the contract), not the locale-rendered
    // string, so the test doesn't depend on the runtime's default locale.
    const spy = vi
      .spyOn(Date.prototype, "toLocaleDateString")
      .mockReturnValue("Jun 15, 2024");
    expect(formatDate("2024-06-15T12:00:00Z")).toBe("Jun 15, 2024");
    expect(spy).toHaveBeenCalledWith(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  });
});

describe("formatDateTime", () => {
  it("returns the fallback for missing input", () => {
    expect(formatDateTime(undefined)).toBe("");
    expect(formatDateTime("")).toBe("");
    expect(formatDateTime("", "-")).toBe("-");
  });

  it("requests a date-and-time format and returns its result", () => {
    const spy = vi
      .spyOn(Date.prototype, "toLocaleString")
      .mockReturnValue("Jun 15, 2024, 12:00 PM");
    expect(formatDateTime("2024-06-15T12:00:00Z")).toBe(
      "Jun 15, 2024, 12:00 PM",
    );
    expect(spy).toHaveBeenCalledWith(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  });
});
