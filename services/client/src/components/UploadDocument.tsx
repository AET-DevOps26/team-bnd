import React, { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { fetchClient } from "../api/client";

interface Props {
  onUploaded?: (documentId: string) => void;
}

type UploadState = "idle" | "uploading" | "success" | "error";

// Only types the backend can extract text from and process (PDF + plain/markdown).
const ACCEPTED_EXTENSIONS = [".pdf", ".txt", ".md", ".markdown"];
const ACCEPTED_MIME_TYPES = new Set([
  "application/pdf",
  "text/plain",
  "text/markdown",
]);
const ACCEPT_ATTR = "application/pdf,text/plain,text/markdown,.md,.markdown";

function isSupportedFile(file: File): boolean {
  if (ACCEPTED_MIME_TYPES.has(file.type)) {
    return true;
  }
  // Fallback for files whose MIME type is missing or generic (e.g. .md).
  const name = file.name.toLowerCase();
  return ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext));
}

// Browsers report .md/.markdown (and sometimes .txt) as application/octet-stream,
// which the backend then stores verbatim and the viewer can't recognise. Derive
// the real type from the extension when the browser type isn't already correct.
const EXTENSION_MIME_TYPES: Record<string, string> = {
  ".md": "text/markdown",
  ".markdown": "text/markdown",
  ".txt": "text/plain",
  ".pdf": "application/pdf",
};

function normalizeType(file: File): File {
  if (ACCEPTED_MIME_TYPES.has(file.type)) return file;
  const name = file.name.toLowerCase();
  const ext = Object.keys(EXTENSION_MIME_TYPES).find((e) => name.endsWith(e));
  if (!ext) return file;
  return new File([file], file.name, { type: EXTENSION_MIME_TYPES[ext] });
}

export default function UploadDocument({ onUploaded }: Props) {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [state, setState] = useState<UploadState>("idle");
  const [errorMessage, setErrorMessage] = useState<string>("");

  async function handleFile(file: File) {
    if (!isSupportedFile(file)) {
      setState("error");
      setErrorMessage("Unsupported file type. Upload a PDF, text or Markdown file.");
      return;
    }

    setState("uploading");
    setErrorMessage("");

    const normalized = normalizeType(file);
    const formData = new FormData();
    formData.append("file", normalized);

    try {
      const { data, error } = await fetchClient.POST(
        "/api/v1/knowledgebase/documents/upload",
        {
          // The generated type models the binary file field as string, but the
          // actual payload is the File, built by hand into FormData below.
          body: { file: normalized as unknown as string },
          bodySerializer: () => formData,
        },
      );

      if (error) {
        setState("error");
        setErrorMessage("Upload failed. Please try again.");
        return;
      }

      setState("success");
      queryClient.invalidateQueries({
        queryKey: ["get", "/api/v1/knowledgebase/documents"],
      });

      if (data?.id && onUploaded) {
        onUploaded(data.id);
      }
    } catch (e: unknown) {
      const code = e instanceof Error ? e.message : "";
      setState("error");
      setErrorMessage(
        code === "NOT_AUTHENTICATED"
          ? "Authentication error. Retrying login..."
          : code === "FORBIDDEN"
            ? "You do not have permission to upload documents."
            : "Upload failed. Please try again.",
      );
    }
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      handleFile(file);
    }
    // Reset so the same file can be re-uploaded if needed
    e.target.value = "";
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files?.[0];
    if (file) {
      handleFile(file);
    }
  }

  function handleDragOver(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(true);
  }

  function handleDragLeave(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(false);
  }

  function openFilePicker() {
    if (state !== "uploading") {
      fileInputRef.current?.click();
    }
  }

  return (
    <div
      className={`upload-area${dragOver ? " upload-area--drag-over" : ""}`}
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onClick={openFilePicker}
      role="button"
      tabIndex={0}
      aria-label="Upload a document by clicking or dropping a file here"
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          openFilePicker();
        }
      }}
    >
      <input
        ref={fileInputRef}
        type="file"
        className="upload-input"
        accept={ACCEPT_ATTR}
        onChange={handleInputChange}
        aria-label="Upload document file"
      />

      {state === "uploading" && <p className="upload-status">Uploading…</p>}

      {state === "error" && (
        <p className="upload-status upload-status--error">{errorMessage}</p>
      )}

      {state === "success" && (
        <p className="upload-status upload-status--success">
          Document uploaded successfully.
        </p>
      )}

      {state !== "uploading" && (
        <button
          type="button"
          className="upload-button"
          onClick={(e) => {
            e.stopPropagation();
            openFilePicker();
          }}
        >
          Upload Document
        </button>
      )}

      {state !== "uploading" && (
        <p className="upload-hint">
          or drag and drop a file here (PDF, text or Markdown)
        </p>
      )}
    </div>
  );
}
