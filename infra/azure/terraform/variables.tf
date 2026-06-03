variable "subscription_id" {
  type        = string
  description = "Azure subscription ID. Leave null to use the subscription from az login or ARM_SUBSCRIPTION_ID."
  default     = null
}

variable "location" {
  type        = string
  description = "Azure region. Default is swedencentral due to subscription policy."
  default     = "swedencentral"
}

variable "resource_group_name" {
  type        = string
  description = "Name of the resource group."
  default     = "devops-rg"
}

variable "create_resource_group" {
  type        = bool
  description = "Create the resource group if true, otherwise use an existing one."
  default     = true
}

variable "name_prefix" {
  type        = string
  description = "Prefix for Azure resource names."
  default     = "alexandria"
}

variable "vm_name" {
  type        = string
  description = "Virtual machine name."
  default     = "alexandria-vm"
}

variable "vm_size" {
  type        = string
  description = "Azure VM size."
  default     = "Standard_D2s_v3"
}

variable "admin_username" {
  type        = string
  description = "Admin username for the VM."
  default     = "azureuser"
}

variable "ssh_public_key_path" {
  type        = string
  description = "Path to the SSH public key used for the VM."
}
