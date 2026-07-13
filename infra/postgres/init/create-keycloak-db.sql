-- Keycloak stores its realm and users in a dedicated database on the shared
-- Postgres instance, mirroring the k8s/Helm setup. Runs only on first init.
CREATE DATABASE keycloak;
