import { describe, it, expect, afterEach } from "vitest";
import { render, cleanup } from "@testing-library/react";
import ArchiveScene from "./ArchiveScene";

afterEach(cleanup);

describe("ArchiveScene", () => {
  it("renders the decorative svg", () => {
    const { container } = render(<ArchiveScene />);
    const svg = container.querySelector("svg.archive-scene");
    expect(svg).not.toBeNull();
    expect(svg?.getAttribute("role")).toBe("presentation");
  });
});
