# Infrastructure provisioning (Terraform + Ansible)

This folder automates Azure VM provisioning and configuration. Terraform creates the VM, network, and security rules. Ansible installs Docker and prepares the VM to run docker compose.

## Prerequisites and why they are needed

- Terraform 1.x, use the latest 1.x release. It talks to Azure and creates resources from code.
- Ansible, it runs configuration steps over SSH and keeps them idempotent.
- Azure CLI login, it provides credentials for the Terraform provider.
- ssh-keygen, used to create a VM SSH keypair that Azure accepts.

## Quick start

1. Login to Azure. The login flow lets you pick the subscription, so no extra command is needed.
   ```bash
   az login
   ```
   If you use a service principal, set the ARM_* environment variables instead.
2. Prepare defaults and keys:
   ```bash
   ./infra/azure/prepare.sh
   ```
3. Provision the VM and run Ansible:
   ```bash
   ./infra/azure/provision.sh
   ```

prepare.sh writes the SSH public key into terraform.tfvars and provision.sh uses the matching private key for Ansible, so you do not need to pass key paths manually.
Ansible waits for SSH to become available before running tasks, which helps when the VM is still booting. The inventory sets ansible_python_interpreter explicitly so interpreter discovery does not change between runs.

## Resource group reuse

provision.sh checks if the resource group already exists and sets create_resource_group accordingly. This lets you re-run the script without Terraform trying to create the RG again. If you want to override the auto detection, set:

```bash
export INFRA_CREATE_RG=true
```

or

```bash
export INFRA_CREATE_RG=false
```

## What prepare.sh does

prepare.sh makes setup less manual and avoids mistakes:

- Generates an RSA SSH keypair under infra/azure/.keys. Azure does not accept ed25519 for Linux VMs, so RSA is used.
- Creates infra/azure/terraform/terraform.tfvars if it does not exist. It writes the location and SSH public key path and keeps defaults for everything else. The default location is swedencentral because of the subscription policy.

Location is required. You can provide it in advance:

```bash
export TF_VAR_location=swedencentral
./infra/azure/prepare.sh
```

You can also set AZURE_LOCATION, it is treated the same way.

If you want a different key location or name, set:

```bash
export INFRA_KEYS_DIR=./infra/azure/.keys
export INFRA_KEY_NAME=azure_vm_rsa
```

## Terraform setup details

terraform.tfvars is created with minimal required values. You can edit it to change names, sizing, the resource group, or the location. If you need to override the selected subscription, set TF_VAR_subscription_id or ARM_SUBSCRIPTION_ID before running prepare.sh.

## Manual provisioning (if needed)

```bash
cd infra/azure/terraform
terraform init
terraform apply
terraform output -raw public_ip_address
```

Create infra/azure/ansible/inventory.ini from inventory.ini.example, fill in the public IP and key path, then run:

```bash
cd infra/azure/ansible
ansible-playbook -i inventory.ini playbook.yml
```

## Cleanup

```bash
cd infra/azure/terraform
terraform destroy
```

## Azure VM deployment (docker compose)

The production compose file is `docker-compose.azure.yml`. It pulls images from GHCR and only publishes ports 80 and 443. It expects a `.env` file on the VM for secrets (Postgres and Keycloak), and it uses `.env.config` for non-secret defaults.

The deploy workflow uses the GitHub Environment `AZURE` and expects:

- `AZURE_PRIVATE_KEY` secret (SSH private key for the VM)
- `AZURE_PUBLIC_IP` variable
- `AZURE_USER` variable (typically azureuser)

When the workflow runs, it copies `docker-compose.azure.yml`, `.env.config`, and `oidc/realm.json` to `~/deploy` on the VM and then runs docker compose with the image tag from the commit SHA.
