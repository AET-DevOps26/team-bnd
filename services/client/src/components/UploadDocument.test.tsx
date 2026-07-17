import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, cleanup, fireEvent } from "@testing-library/react";
import UploadDocument from "./UploadDocument";
import { renderWithProviders } from "../test/harness";
import * as clientModule from "../api/client";

vi.mock("../api/client");

const api = clientModule as unknown as typeof import("../api/__mocks__/client");

function fileInput() {
  return screen.getByLabelText<HTMLInputElement>("Upload document file");
}

function uploadArea() {
  return screen.getByRole("button", { name: /Upload a document/ });
}

function pdf(name = "doc.pdf") {
  return new File(["data"], name, { type: "application/pdf" });
}

beforeEach(() => {
  api.resetApiMock();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("UploadDocument", () => {
  it("renders the upload affordances", () => {
    renderWithProviders(<UploadDocument />);
    expect(
      screen.getByRole("button", { name: "Upload Document" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/drag and drop a file here/i)).toBeInTheDocument();
  });

  it("rejects an unsupported file type", () => {
    renderWithProviders(<UploadDocument />);
    fireEvent.change(fileInput(), {
      target: { files: [new File(["x"], "photo.png", { type: "image/png" })] },
    });
    expect(
      screen.getByText(
        "Unsupported file type. Upload a PDF, text or Markdown file.",
      ),
    ).toBeInTheDocument();
  });

  it("uploads a supported file and reports success", async () => {
    const onUploaded = vi.fn();
    api.fetchClient.POST.mockResolvedValue({ data: { id: "doc-9" } });
    renderWithProviders(<UploadDocument onUploaded={onUploaded} />);

    fireEvent.change(fileInput(), { target: { files: [pdf()] } });

    expect(
      await screen.findByText("Document uploaded successfully."),
    ).toBeInTheDocument();
    expect(onUploaded).toHaveBeenCalledWith("doc-9");
  });

  it("normalizes a markdown file the browser reported as octet-stream", async () => {
    api.fetchClient.POST.mockResolvedValue({ data: { id: "md-1" } });
    renderWithProviders(<UploadDocument />);
    fireEvent.change(fileInput(), {
      target: {
        files: [
          new File(["# hi"], "notes.md", {
            type: "application/octet-stream",
          }),
        ],
      },
    });
    expect(
      await screen.findByText("Document uploaded successfully."),
    ).toBeInTheDocument();
    expect(api.fetchClient.POST).toHaveBeenCalledOnce();
  });

  it("shows an error when the upload returns an error payload", async () => {
    api.fetchClient.POST.mockResolvedValue({ error: { code: "boom" } });
    renderWithProviders(<UploadDocument />);
    fireEvent.change(fileInput(), { target: { files: [pdf()] } });
    expect(
      await screen.findByText("Upload failed. Please try again."),
    ).toBeInTheDocument();
  });

  it.each([
    ["NOT_AUTHENTICATED", "Authentication error. Retrying login..."],
    ["FORBIDDEN", "You do not have permission to upload documents."],
    ["nope", "Upload failed. Please try again."],
  ])("maps a thrown %s to a message", async (code, message) => {
    api.fetchClient.POST.mockRejectedValue(new Error(code));
    renderWithProviders(<UploadDocument />);
    fireEvent.change(fileInput(), { target: { files: [pdf()] } });
    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("uploads a file dropped onto the area", async () => {
    api.fetchClient.POST.mockResolvedValue({ data: { id: "drop-1" } });
    renderWithProviders(<UploadDocument />);
    const area = uploadArea();
    fireEvent.dragOver(area);
    fireEvent.dragLeave(area);
    fireEvent.drop(area, { dataTransfer: { files: [pdf()] } });
    expect(
      await screen.findByText("Document uploaded successfully."),
    ).toBeInTheDocument();
  });

  it("opens the file picker via keyboard and click", async () => {
    renderWithProviders(<UploadDocument />);
    const clickSpy = vi.spyOn(fileInput(), "click");
    fireEvent.keyDown(uploadArea(), { key: "Enter" });
    fireEvent.keyDown(uploadArea(), { key: " " });
    fireEvent.click(uploadArea());
    fireEvent.click(screen.getByRole("button", { name: "Upload Document" }));
    expect(clickSpy).toHaveBeenCalled();
  });
});
