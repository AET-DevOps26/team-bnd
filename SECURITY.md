# Security Policy

## Supported versions

This is a university course project for the TUM DevOps lab. Only the `main` branch is supported. There are no LTS releases, no backports, and support ends when the course concludes. We do not publish patched releases for older versions.

If you are running anything other than the latest commit on `main`, you are on your own.

## Reporting a vulnerability

The preferred channel is GitHub's private vulnerability reporting. Open the repo's **Security** tab, choose **Report a vulnerability**, and follow the prompts. This keeps the report private to the maintainers and lets us discuss details before anything is disclosed publicly.

If private reporting is not enabled on the repo, fall back to opening a private GitHub security advisory directly, or contact one of the maintainers listed in `.github/CODEOWNERS` through Artemis until the channel is restored.

Please include enough detail to reproduce the issue: the affected component (client, Spring services, GenAI, Docker setup, k8s manifests, CI), the commit SHA you tested, and a minimal proof of concept if you have one.

## Response expectation

This is a student project maintained alongside coursework, so we cannot promise rapid SLAs. We will acknowledge a report within a few days and aim to ship a fix or mitigation within the current course iteration. For trivial issues we will push a fix on the next branch; for anything sensitive we will coordinate disclosure with the reporter first.

## Scope

In scope: anything in this repository, including services, infrastructure as code, CI workflows, and the OpenAPI spec. Out of scope: dependencies vendored verbatim from upstream Keycloak realm exports and any third-party image we merely consume in `docker-compose.yml` or Helm charts -- report those upstream.
