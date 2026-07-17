# Azure deployment (Terraform + Ansible)

This sets up a single VM on Azure and runs the whole stack on it with Docker Compose. Terraform creates the VM and networking, Ansible installs Docker and does the deploy, and a small script builds the `.env` so you are not copy-pasting secrets by hand.

## What you need

- Terraform 1.x
- Ansible (the `ansible-playbook` command)
- Azure CLI, logged in with `az login`. A service principal works too, just set the usual `ARM_*` variables.
- `ssh-keygen`. Azure only accepts RSA keys for Linux VMs, so we generate one.

## Deploy

```bash
az login
./infra/azure/provision.sh
```

`provision.sh` does the following end to end:

1. Generates an SSH keypair and a `terraform.tfvars` if you do not have them yet (that part is `prepare.sh`, which provision.sh calls for you).
2. Runs `terraform apply` to create the VM.
3. Asks a few questions and writes the `.env` (see below).
4. Pauses so you can review or edit the `.env` before anything is deployed.
5. Runs the Ansible playbook: installs Docker, copies the compose files and infra config to the VM, and starts everything with `docker compose up -d`.

When it finishes, the site is up at the base URL you gave it.

## The .env and secrets

`generate-env.sh` builds the `.env` (provision.sh calls it, or you can run it on its own). It asks for:

- HTTP or Let's Encrypt,
- the base URL, or the domain if you picked Let's Encrypt,
- your LLM API key (leave it blank and add it later if you want).

Every value that needs to be a random secret (the Postgres password, Keycloak admin password, the Grafana secrets, the internal HMAC secret, and so on) is generated for you. Which vars those are is read from `.env.example`: anything using the shared secret placeholder there gets a fresh value, so there is no hardcoded list in the script to keep in sync.

When running again, it keeps the existing file instead of rolling new secrets, otherwise the Postgres password would stop matching the data already on the disk. Force a clean regenerate with `OVERWRITE_ENV=true` and wipe the VM's Postgres volume if you do.

## Base URL and TLS

There is one variable for the public address: `PUBLIC_BASE_URL` (scheme + host, no trailing slash). Everything the browser touches (the OIDC issuer, Keycloak, Grafana, CORS) is derived from it in `docker-compose.yml`, so you set it once. Locally it defaults to `http://localhost`.

For HTTPS, pick Let's Encrypt in generate-env.sh (or set `PUBLIC_DOMAIN` and `ACME_EMAIL`). provision.sh ships `docker-compose.letsencrypt.yml` to the VM as `docker-compose.override.yml`, so a plain `docker compose up` there merges it and Traefik serves HTTPS with a real cert and redirects port 80 to 443. Point the domain's DNS A record at the VM first, otherwise the ACME challenge cannot complete.

## Redeploys and CI

Once the VM is provisioned, pushes to `main` redeploy automatically when the `DEPLOY_AZURE` variable is set. The `Deploy to Azure VM` job runs the same Ansible deploy role with `--tags deploy`, so CI follows the exact same steps as provision.sh instead of its own copy commands. It assumes the one-time setup (Docker, the `.env`) is already done, so it never touches the `.env` or the TLS override on the VM. It just refreshes the compose files and infra config and re-deploys. It needs the `AZURE` environment with the `AZURE_PRIVATE_KEY` secret and the `AZURE_PUBLIC_IP` and `AZURE_USER` variables.

## Update a running deployment by hand

If the VM is already up and you just want to push new compose or infra changes without touching Terraform or the `.env`, run the deploy role on its own (this is exactly what the CI also does).

You need an inventory pointing at the VM. provision.sh already wrote one at `infra/azure/ansible/inventory.ini`. If it is not there, copy `inventory.ini.example` next to it and fill in the public IP and the key path.

Then:

```bash
cd infra/azure/ansible
ANSIBLE_CONFIG=ansible.cfg ansible-playbook -i inventory.ini playbook.yml \
	--tags deploy \
	-e repo_root="$(git rev-parse --show-toplevel)"
```

`--tags deploy` skips the one-time Docker install. Leaving `env_file` unset (as above) is what tells the deploy role to keep the VM's own `.env` and TLS override instead of overwriting them.

## Tear everything down again

```bash
cd infra/azure/terraform
terraform destroy
```

This removes the VM, its disk, the network, and the public IP. The resource group is only deleted if Terraform created it in the first place. The generated `.env` and the SSH keys stay on your machine. Delete `infra/azure/.env` and `infra/azure/.keys/` by hand if you want them gone too.

## Handy overrides

- Region defaults to `swedencentral` because of the subscription policy. Change it with `TF_VAR_location`.
- provision.sh reuses an existing resource group if it finds one. Force the choice with `INFRA_CREATE_RG=true` or `INFRA_CREATE_RG=false`.
