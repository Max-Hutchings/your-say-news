output "deployment_contract" {
  description = "Non-secret values consumed by the development deployment workflow."
  value = {
    environment        = var.environment
    api_hostname       = var.api_hostname
    ssh_hostname       = var.ssh_hostname
    hcloud_location    = var.hcloud_location
    hcloud_server_type = var.hcloud_server_type
    aiven_service_name = var.aiven_service_name
    media_bucket_name  = var.r2_media_bucket_name
    backup_bucket_name = var.r2_backup_bucket_name
  }
}

output "common_labels" {
  description = "Low-cardinality labels shared by repository-local infrastructure modules."
  value       = local.common_labels
}

output "hcloud_host" {
  description = "Hetzner host identifiers when host provisioning is enabled."
  value = var.enable_hcloud_host ? {
    id                = module.compose_host[0].server_id
    name              = module.compose_host[0].server_name
    ipv4_address      = module.compose_host[0].ipv4_address
    ipv6_address      = module.compose_host[0].ipv6_address
    firewall_id       = module.compose_host[0].firewall_id
    cloud_init_sha256 = module.compose_host[0].cloud_init_sha256
  } : null
}

output "postgresql" {
  description = "Non-secret PostgreSQL connection contract when Aiven provisioning is enabled."
  value = var.enable_aiven_postgresql ? {
    service_name = module.postgresql[0].service_name
    cloud_name   = module.postgresql[0].cloud_name
    host         = module.postgresql[0].service_host
    port         = module.postgresql[0].service_port
    database     = module.postgresql[0].database_name
    users        = module.postgresql[0].database_user_names
  } : null
}

output "r2" {
  description = "Non-secret R2 bucket contract when R2 provisioning is enabled."
  value = var.enable_r2_buckets ? {
    media_bucket   = module.media_bucket[0].bucket_name
    backup_bucket  = module.backup_bucket[0].bucket_name
    jurisdiction   = module.media_bucket[0].jurisdiction
    media_endpoint = module.media_bucket[0].s3_endpoint
  } : null
}

output "cloudflare_tunnel" {
  description = "Non-secret Tunnel contract when Tunnel provisioning is enabled."
  value = var.enable_cloudflare_tunnel ? {
    id        = module.api_tunnel[0].tunnel_id
    hostnames = module.api_tunnel[0].route_hostnames
    routes    = module.api_tunnel[0].route_services
  } : null
}

output "cloudflare_tunnel_token" {
  description = "Sensitive connector token for the VM deployment secret workflow."
  value       = var.enable_cloudflare_tunnel ? module.api_tunnel[0].tunnel_token : null
  sensitive   = true
}
