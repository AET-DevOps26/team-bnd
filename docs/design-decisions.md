# Design Decisions

The non-obvious decisions behind Alexandria: the ones driven by a constraint we hit, a tradeoff we argued about in review, or a subtle gotcha that would otherwise stay buried in a PR thread. Each entry describes the current design and the reason you would not guess from the code, and links the PR or issue where it was decided.

## Working within the STUD cluster's limits

- NetworkPolicies default to `allowFromAnywhere: true` for the public tier instead of scoping to the ingress controller, because on the STUD cluster we have no permission to inspect the ingress controller's namespace or labels. A guessed selector silently 504s all external traffic (we tried it and got a gateway timeout). The internal tier stays locked down, and the render fails fast if selectors are left empty so the default-deny can't be quietly defeated. [PR #302](https://github.com/AET-DevOps26/team-bnd/pull/302)
- Single-replica Spring/GenAI/client deployments use `strategy: Recreate`, not the default rolling update. The namespace ResourceQuota caps limits, and a rolling update briefly runs a second pod whose limits stack on the old one, tripping the quota and wedging `helm upgrade`. Rolling update comes back automatically once a service scales out on the HPA. [PR #315](https://github.com/AET-DevOps26/team-bnd/pull/315), [PR #322](https://github.com/AET-DevOps26/team-bnd/pull/322)
- The chart runs its own Prometheus/Grafana with a static scrape config from a ConfigMap rather than the cluster's Prometheus Operator. The operator and its Prometheus are cluster-owned, so we don't control their scrape scope or alerting and shouldn't tie our monitoring to shared infrastructure. [PR #302](https://github.com/AET-DevOps26/team-bnd/pull/302)

## Auth and service boundaries

- Users are provisioned into the DB just-in-time on their first authenticated request, and `username`/`email` are treated as nullable because the OpenID spec doesn't guarantee those claims are present. [PR #68](https://github.com/AET-DevOps26/team-bnd/pull/68)
- Service-to-service `/internal/**` calls are authenticated with HMAC-signed headers plus a timestamp and a configurable max-skew window (default 300s) to block replay, using one shared secret across services. [PR #300](https://github.com/AET-DevOps26/team-bnd/pull/300)

## Data model, migrations, transactions

- Schema is managed with Flyway and Hibernate runs `ddl-auto=validate`. The initial migration is idempotent and covers both a fresh install and an upgrade from the pre-split monolith: on an old DB it rewrites the ownership columns (`owner_id` to `owner_subject`, etc.) and moves tables into the per-service schema with `ALTER TABLE ... SET SCHEMA`. Because the three services start in any order, the knowledgebase/qa migrations look up `user_service.users` and fall back to `public.users` if the user-service migration hasn't run yet. [PR #230](https://github.com/AET-DevOps26/team-bnd/pull/230)
- The async processing pipeline runs each step (summary, entities, tags, index) in its own transaction via a self-proxy; the dispatching method is intentionally not transactional. One shared transaction would make the client see all-PENDING then a single flip at the end, and a mid-pipeline crash would roll every status back to PENDING. Separate transactions commit each status as it finishes and leave completed steps intact on a crash. [PR #339](https://github.com/AET-DevOps26/team-bnd/pull/339), [PR #300](https://github.com/AET-DevOps26/team-bnd/pull/300)
- `deleteUser` fans out HTTP deletes to the peer services outside any `@Transactional` method, so a hung peer can't hold a Postgres connection and exhaust the Hikari pool. The peer deletes are idempotent (keyed on the OIDC subject) and the local user row is kept as a retry anchor, dropped only once both peers succeed; a partial failure is surfaced so the delete can be retried until it converges. [PR #210](https://github.com/AET-DevOps26/team-bnd/pull/210)

## GenAI retrieval

- Semantic search scores are not raw cosine. Qwen3 similarities sit in a narrow band (unrelated text still scores ~0.15, a strong match ~0.35-0.6), so showing raw cosine makes every result look mediocre. The score is calibrated onto [0,1] via `SEARCH_SCORE_FLOOR`/`SEARCH_SCORE_CEILING`, and the mapping is monotonic so ranking is unchanged. [PR #324](https://github.com/AET-DevOps26/team-bnd/pull/324)
- Retrieval dedups to one result per document using Weaviate's `group_by` on `object_key`, so a document with many close chunks can't crowd out the rest under the limit. An empty `objectKeys` scope returns nothing rather than searching the whole collection, so a caller that forgets to scope can't leak across users. [PR #243](https://github.com/AET-DevOps26/team-bnd/pull/243)
- Semantic search falls back to keyword search when GenAI returns nothing or the index is empty, with `score`/`snippet` left null, and the response carries an explicit `fallbackUsed` flag so the client can tell a fallback from a ranked result. [PR #270](https://github.com/AET-DevOps26/team-bnd/pull/270)
- Tag reuse is driven by Spring passing the user's existing tags into the GenAI request (`knownTags`) to bias the prompt, keeping the dependency one-way so GenAI never calls back into Spring. Tags are lowercased for deduplication (`Finance` and `finance` collapse to one); display casing is left to the client. [PR #242](https://github.com/AET-DevOps26/team-bnd/pull/242)

## API contract

- OpenAPI drift is caught by regenerating the spec/client and failing on a dirty tree: a pre-commit hook regenerates on commit, and CI runs `git diff --exit-code` on the generated spec so a stale checked-in client fails the build. [PR #168](https://github.com/AET-DevOps26/team-bnd/pull/168), [PR #293](https://github.com/AET-DevOps26/team-bnd/pull/293)

## Local dev and CI

- The Playwright runner is an optional `e2e` compose profile that runs inside the docker network, so its `baseURL` targets the `client` container name, not `localhost`. The Traefik proxy is only reachable as `localhost` from outside the network, and `docker compose run` couldn't be used because it only starts services already defined in the compose file. [PR #126](https://github.com/AET-DevOps26/team-bnd/pull/126)
- The Spring-to-GenAI HTTP clients are pinned to HTTP/1.1. The default HTTP/2 upgrade request was sent alongside the body, uvicorn rejected the upgrade and dropped the body, and FastAPI then failed with a 422 missing-field error. [PR #209](https://github.com/AET-DevOps26/team-bnd/pull/209)

## Client

- The client API base URL is a build-time `VITE_API_URL` (falling back to same-origin). Its usefulness is debatable since it's baked in at build, but the course guidelines require build-time `VITE_` variables, so it was added for that reason; the gateway CORS origin is not allowed to default to `localhost` on the cluster. [PR #326](https://github.com/AET-DevOps26/team-bnd/pull/326)
