output "deployment_contract" {
  description = "Non-secret values consumed by the development deployment workflow."
  value = {
    environment        = var.environment
    api_hostname       = var.api_hostname
    hcloud_location    = var.hcloud_location
    hcloud_server_type = var.hcloud_server_type
    aiven_service_name = var.aiven_service_name
    r2_bucket_name     = var.r2_bucket_name
  }
}

output "common_labels" {
  description = "Low-cardinality labels shared by repository-local infrastructure modules."
  value       = local.common_labels
}
