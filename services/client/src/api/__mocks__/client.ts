import { vi } from "vitest";

// Manual mock for ../api/client used by component tests via vi.mock("../api/client").
// It lets a test set the react-query state each hook returns (keyed by the path
// string the component passes), and exposes spies so tests can assert a mutation
// fired. Mutations invoke their onSuccess callbacks synchronously so success flows
// (state updates, cache invalidation) run without any real network.

export interface MockQuery {
  data?: unknown;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
}

export interface MockMutation {
  isPending?: boolean;
  isError?: boolean;
  error?: unknown;
  result?: unknown;
}

const queries = new Map<string, MockQuery>();
const mutations = new Map<string, MockMutation>();
const mutateSpies = new Map<string, ReturnType<typeof vi.fn>>();
const resetSpies = new Map<string, ReturnType<typeof vi.fn>>();

// Some components hit the same path with different verbs (e.g. PATCH vs DELETE on
// /documents/{id}), so everything is keyed by "method path", not path alone.
function key(method: string, path: string): string {
  return `${method} ${path}`;
}

export function setQuery(method: string, path: string, q: MockQuery): void {
  queries.set(key(method, path), q);
}

export function setMutation(
  method: string,
  path: string,
  m: MockMutation,
): void {
  mutations.set(key(method, path), m);
}

export function mutateSpy(
  method: string,
  path: string,
): ReturnType<typeof vi.fn> {
  const k = key(method, path);
  let spy = mutateSpies.get(k);
  if (!spy) {
    spy = vi.fn();
    mutateSpies.set(k, spy);
  }
  return spy;
}

export function resetSpy(
  method: string,
  path: string,
): ReturnType<typeof vi.fn> {
  const k = key(method, path);
  let spy = resetSpies.get(k);
  if (!spy) {
    spy = vi.fn();
    resetSpies.set(k, spy);
  }
  return spy;
}

export function resetApiMock(): void {
  queries.clear();
  mutations.clear();
  mutateSpies.clear();
  resetSpies.clear();
  fetchClient.GET.mockReset().mockResolvedValue({});
  fetchClient.POST.mockReset().mockResolvedValue({});
  fetchClient.PUT.mockReset().mockResolvedValue({});
  fetchClient.PATCH.mockReset().mockResolvedValue({});
  fetchClient.DELETE.mockReset().mockResolvedValue({});
}

interface SuccessOpts {
  onSuccess?: (data: unknown) => void;
}

function useQuery(method: string, path: string) {
  const q = queries.get(key(method, path)) ?? {};
  return {
    data: q.data,
    isLoading: q.isLoading ?? false,
    isError: q.isError ?? false,
    error: q.error ?? null,
  };
}

function useMutation(method: string, path: string, opts?: SuccessOpts) {
  const m = mutations.get(key(method, path)) ?? {};
  return {
    mutate: (vars: unknown, callOpts?: SuccessOpts) => {
      const spy = mutateSpy(method, path) as unknown as (
        ...args: unknown[]
      ) => void;
      spy(vars, callOpts);
      opts?.onSuccess?.(m.result);
      callOpts?.onSuccess?.(m.result);
    },
    isPending: m.isPending ?? false,
    isError: m.isError ?? false,
    error: m.error ?? null,
    reset: resetSpy(method, path),
  };
}

const $api = { useQuery, useMutation };

export const fetchClient = {
  GET: vi.fn().mockResolvedValue({}),
  POST: vi.fn().mockResolvedValue({}),
  PUT: vi.fn().mockResolvedValue({}),
  PATCH: vi.fn().mockResolvedValue({}),
  DELETE: vi.fn().mockResolvedValue({}),
};

export default $api;
