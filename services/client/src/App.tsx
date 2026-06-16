import React, { useState } from "react";
import DocumentTree from "./components/DocumentTree";
import DocumentDetail from "./components/DocumentDetail";

export default function App() {
  const [selectedDocumentId, setSelectedDocumentId] = useState<string | null>(
    null,
  );

  return (
    <div className="app">
      <header className="app-header">
        <h1>Alexandria — Document Summarization</h1>
      </header>
      <div className="app-body">
        <DocumentTree
          selectedId={selectedDocumentId}
          onSelect={setSelectedDocumentId}
        />
        <main className="app-main">
          <DocumentDetail documentId={selectedDocumentId} />
        </main>
      </div>
    </div>
  );
}
