#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
terraform_dir="${script_dir}/terraform"
ansible_dir="${script_dir}/ansible"
prepare_script="${script_dir}/prepare.sh"
generate_env_script="${script_dir}/generate-env.sh"
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

if [[ ! -x "${generate_env_script}" ]]; then
	echo "generate-env.sh is missing or not executable." >&2
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

repo_root="$(cd "${script_dir}/../.." && pwd)"
env_file="${ENV_OUTPUT_PATH:-${script_dir}/.env}"

echo "Preparing the deployment .env..."
ENV_OUTPUT_PATH="${env_file}" DEFAULT_BASE_URL="http://${public_ip}" "${generate_env_script}"

if [[ ! -f "${env_file}" ]]; then
	echo "Expected ${env_file} after generate-env.sh, but it is missing. Aborting." >&2
	exit 1
fi

if [[ -t 0 ]]; then
	echo ""
	echo "The deployment environment file is ready at ${env_file}."
	echo "Edit it now if you want to change anything (base URL, secrets, LLM key)."
	read -r -p "Press Enter to start the deployment..." _ || true
fi

if grep -qE '^PUBLIC_DOMAIN=' "${env_file}"; then
	use_letsencrypt="true"
else
	use_letsencrypt="false"
fi

echo "Running Ansible playbook..."
ANSIBLE_CONFIG="${ansible_dir}/ansible.cfg" ansible-playbook \
	-i "${inventory_file}" "${ansible_dir}/playbook.yml" \
	-e "repo_root=${repo_root}" \
	-e "env_file=${env_file}" \
	-e "use_letsencrypt=${use_letsencrypt}"
