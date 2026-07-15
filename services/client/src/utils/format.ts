/**
 * Formats an ISO timestamp as a localized date and time.
 * Returns `fallback` when the input is missing or empty.
 */
export function formatDateTime(isoString?: string, fallback = ""): string {
  if (!isoString) return fallback;
  return new Date(isoString).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Formats an ISO timestamp as a localized date without the time component.
 * Returns `fallback` when the input is missing or empty.
 */
export function formatDate(isoString?: string, fallback = ""): string {
  if (!isoString) return fallback;
  return new Date(isoString).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

/**
 * Formats a byte count as a human-readable size string (B / KB / MB).
 * Returns `fallback` when the input is nullish.
 */
export function formatBytes(bytes?: number, fallback = ""): string {
  if (bytes == null) return fallback;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
