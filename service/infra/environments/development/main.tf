locals {
  common_labels = {
    application = "your-say-news"
    environment = var.environment
    managed_by  = "terraform"
  }
}

module "compose_host" {
  count  = var.enable_hcloud_host ? 1 : 0
  source = "../../modules/linux-compose-host"

  name          = var.hcloud_server_name
  location      = var.hcloud_location
  server_type   = var.hcloud_server_type
  image         = var.hcloud_image
  ssh_key_names = var.hcloud_ssh_key_names
  ipv4_enabled  = var.hcloud_ipv4_enabled
  labels        = local.common_labels
}

module "postgresql" {
  count  = var.enable_aiven_postgresql ? 1 : 0
  source = "../../modules/aiven-postgresql"

  project_name        = var.aiven_project_name
  service_name        = var.aiven_service_name
  cloud_name          = var.aiven_cloud_name
  plan                = var.aiven_plan
  database_name       = var.aiven_database_name
  database_user_names = var.aiven_database_user_names
}

module "media_bucket" {
  count  = var.enable_r2_buckets ? 1 : 0
  source = "../../modules/r2-private-bucket"

  account_id      = var.cloudflare_account_id
  bucket_name     = var.r2_media_bucket_name
  cors_rules      = var.r2_media_cors_rules
  lifecycle_rules = var.r2_media_lifecycle_rules
}

module "backup_bucket" {
  count  = var.enable_r2_buckets ? 1 : 0
  source = "../../modules/r2-private-bucket"

  account_id      = var.cloudflare_account_id
  bucket_name     = var.r2_backup_bucket_name
  lifecycle_rules = var.r2_backup_lifecycle_rules
}

module "api_tunnel" {
  count  = var.enable_cloudflare_tunnel ? 1 : 0
  source = "../../modules/cloudflare-api-tunnel"

  account_id  = var.cloudflare_account_id
  zone_id     = var.cloudflare_zone_id
  tunnel_name = var.cloudflare_tunnel_name
  routes = {
    (var.api_hostname) = "http://post-service:8082"
    (var.ssh_hostname) = "ssh://localhost:22"
  }
}
