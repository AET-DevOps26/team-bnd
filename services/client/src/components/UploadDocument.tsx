import React, { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { fetchClient } from "../api/client";

interface Props {
  onUploaded?: (documentId: string) => void;
}

type UploadState = "idle" | "uploading" | "success" | "error";

export default function UploadDocument({ onUploaded }: Props) {
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);
  const [state, setState] = useState<UploadState>("idle");
  const [errorMessage, setErrorMessage] = useState<string>("");

  async function handleFile(file: File) {
    setState("uploading");
    setErrorMessage("");

    const formData = new FormData();
    formData.append("file", file);

    try {
      const { data, error } = await fetchClient.POST(
        "/api/v1/knowledgebase/documents/upload",
        {
          body: { file } as any,
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
    } catch (e: any) {
      setState("error");
      setErrorMessage(
        e?.message === "NOT_AUTHENTICATED"
          ? "Authentication error. Retrying login..."
          : e?.message === "FORBIDDEN"
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

  return (
    <div
      className={`upload-area${dragOver ? " upload-area--drag-over" : ""}`}
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
    >
      <input
        ref={fileInputRef}
        type="file"
        className="upload-input"
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
          onClick={() => fileInputRef.current?.click()}
        >
          Upload Document
        </button>
      )}

      {state !== "uploading" && (
        <p className="upload-hint">or drag and drop a file here</p>
      )}
    </div>
  );
}
