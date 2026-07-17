import { describe, it, expect, afterEach } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import UploadDocument from "./UploadDocument";

function renderUpload() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <UploadDocument />
    </QueryClientProvider>,
  );
}

afterEach(cleanup);

describe("UploadDocument", () => {
  it("renders the upload affordances", () => {
    renderUpload();
    expect(
      screen.getByRole("button", { name: "Upload Document" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/drag and drop a file here/i),
    ).toBeInTheDocument();
  });

  it("rejects an unsupported file type without uploading", () => {
    renderUpload();
    const input = screen.getByLabelText<HTMLInputElement>(
      "Upload document file",
    );
    const file = new File(["binary"], "photo.png", { type: "image/png" });
    fireEvent.change(input, { target: { files: [file] } });
    expect(
      screen.getByText(
        "Unsupported file type. Upload a PDF, text or Markdown file.",
      ),
    ).toBeInTheDocument();
  });
});
