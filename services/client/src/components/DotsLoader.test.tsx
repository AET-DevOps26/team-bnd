import { describe, it, expect, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import DotsLoader from "./DotsLoader";

afterEach(cleanup);

describe("DotsLoader", () => {
  it("exposes an accessible loading status", () => {
    render(<DotsLoader />);
    const status = screen.getByRole("status");
    expect(status).toBeInTheDocument();
    expect(status).toHaveAttribute("aria-label", "Loading");
  });

  it("renders three dots", () => {
    const { container } = render(<DotsLoader />);
    expect(container.querySelectorAll(".dots-loader__dot")).toHaveLength(3);
  });
});
