#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
terraform_dir="${script_dir}/terraform"
keys_dir="${INFRA_KEYS_DIR:-${script_dir}/.keys}"
key_name="${INFRA_KEY_NAME:-azure_vm_rsa}"
tfvars_file="${terraform_dir}/terraform.tfvars"

mkdir -p "${keys_dir}"
keys_dir="$(cd "${keys_dir}" && pwd)"
private_key_path="${keys_dir}/${key_name}"
public_key_path="${private_key_path}.pub"

generate_keypair() {
  ssh-keygen -t rsa -b 4096 -f "${private_key_path}" -N "" >/dev/null
}

if [[ -f "${public_key_path}" ]]; then
  if ! ssh-keygen -lf "${public_key_path}" | grep -q "RSA"; then
    echo "Existing key is not RSA, replacing it so Azure accepts it."
    rm -f "${private_key_path}" "${public_key_path}"
    generate_keypair
  fi
else
  echo "Generating RSA SSH keypair for the VM."
  generate_keypair
fi

if [[ ! -f "${tfvars_file}" ]]; then
  location="${TF_VAR_location:-${AZURE_LOCATION:-swedencentral}}"

  subscription_id="${TF_VAR_subscription_id:-${ARM_SUBSCRIPTION_ID:-}}"

  {
    if [[ -n "${subscription_id}" ]]; then
      echo "subscription_id = \"${subscription_id}\""
    fi
    echo "location = \"${location}\""
    echo "ssh_public_key_path = \"${public_key_path}\""
  } > "${tfvars_file}"

  echo "Created ${tfvars_file} with defaults. Edit it if you want to change names or sizing."
else
  if ! grep -q "ssh_public_key_path" "${tfvars_file}"; then
    echo "terraform.tfvars exists but is missing ssh_public_key_path. Add it or remove the file so prepare.sh can recreate it." >&2
    exit 1
  fi
fi
