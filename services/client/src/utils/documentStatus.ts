import type { components } from "../api/schema";

type Document = components["schemas"]["Document"];

const POLL_INTERVAL_MS = 3000;

// ~5 min at POLL_INTERVAL_MS
const MAX_POLLS = 100;

export function isProcessing(doc: Document): boolean {
  return (
    doc.summary?.status === "PENDING" ||
    doc.entitiesStatus === "PENDING" ||
    doc.tagsStatus === "PENDING"
  );
}

// refetchInterval callback: keep polling while something is PENDING, but stop once
// nothing is pending OR we have polled MAX_POLLS times
export function pollWhileProcessing(
  anyPending: boolean,
  dataUpdateCount: number,
): number | false {
  if (!anyPending) return false;
  if (dataUpdateCount >= MAX_POLLS) return false;
  return POLL_INTERVAL_MS;
}
