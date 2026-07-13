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
