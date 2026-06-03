output "public_ip_address" {
  description = "Public IP address of the VM."
  value       = azurerm_public_ip.vm_public_ip.ip_address
}

output "admin_username" {
  description = "Admin username for the VM."
  value       = var.admin_username
}
