#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
terraform_dir="${script_dir}/terraform"
ansible_dir="${script_dir}/ansible"
prepare_script="${script_dir}/prepare.sh"
runtime_tfvars="${terraform_dir}/runtime.auto.tfvars"

if ! command -v terraform > /dev/null 2>&1; then
	echo "terraform is required but not installed." >&2
	exit 1
fi

if ! command -v ansible-playbook > /dev/null 2>&1; then
	echo "ansible-playbook is required but not installed." >&2
	exit 1
fi

if [[ ! -x "${prepare_script}" ]]; then
	echo "prepare.sh is missing or not executable." >&2
	exit 1
fi

"${prepare_script}"

keys_dir="${INFRA_KEYS_DIR:-${script_dir}/.keys}"
key_name="${INFRA_KEY_NAME:-azure_vm_rsa}"
keys_dir="$(cd "${keys_dir}" && pwd)"
private_key_path="${keys_dir}/${key_name}"

get_tfvar() {
	local key="$1"
	local file="${terraform_dir}/terraform.tfvars"
	if [[ -f "${file}" ]]; then
		local line
		line="$(grep -E "^${key}[[:space:]]*=" "${file}" | tail -n 1 || true)"
		if [[ -n "${line}" ]]; then
			echo "${line}" | sed -E 's/^[^"]*"([^"]*)".*/\1/'
			return 0
		fi
	fi
	return 1
}

resource_group_name="$(get_tfvar resource_group_name || true)"
if [[ -z "${resource_group_name}" ]]; then
	resource_group_name="devops-rg"
fi

if [[ -n "${INFRA_CREATE_RG:-}" ]]; then
	create_resource_group="${INFRA_CREATE_RG}"
else
	if ! command -v az > /dev/null 2>&1; then
		echo "az is required to auto-detect the resource group. Set INFRA_CREATE_RG=true or false." >&2
		exit 1
	fi
	if ! rg_exists="$(az group exists --name "${resource_group_name}")"; then
		echo "Failed to check if the resource group exists. Make sure az login is complete." >&2
		exit 1
	fi
	if [[ "${rg_exists}" == "true" ]]; then
		create_resource_group="false"
	else
		create_resource_group="true"
	fi
fi

cat > "${runtime_tfvars}" << EOF
create_resource_group = ${create_resource_group}
EOF

echo "Running Terraform in ${terraform_dir}..."
pushd "${terraform_dir}" > /dev/null
terraform init -input=false
terraform apply -auto-approve -input=false
public_ip="$(terraform output -raw public_ip_address)"
admin_user="$(terraform output -raw admin_username)"
popd > /dev/null

if [[ -z "${public_ip}" ]]; then
	echo "Terraform output public_ip_address is empty. Aborting." >&2
	exit 1
fi

inventory_file="${ansible_dir}/inventory.ini"
cat > "${inventory_file}" << EOF
[azure]
${public_ip}

[azure:vars]
ansible_user=${admin_user}
ansible_ssh_private_key_file=${private_key_path}
ansible_python_interpreter=/usr/bin/python3
EOF

extra_vars=()
if [[ -n "${ENV_TEMPLATE_PATH:-}" ]]; then
	extra_vars+=("-e" "env_template_path=${ENV_TEMPLATE_PATH}")
fi
if [[ -n "${ENV_TARGET_PATH:-}" ]]; then
	extra_vars+=("-e" "env_target_path=${ENV_TARGET_PATH}")
fi

echo "Running Ansible playbook..."
ANSIBLE_CONFIG="${ansible_dir}/ansible.cfg" ansible-playbook -i "${inventory_file}" "${ansible_dir}/playbook.yml" "${extra_vars[@]}"
