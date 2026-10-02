locals {
  common_labels = {
    application = "your-say-news"
    environment = var.environment
    managed_by  = "terraform"
  }

  compose_host_cloud_init = templatefile("${path.module}/cloud-init.yaml.tftpl", {
    bootstrap_script = file("${path.module}/files/bootstrap-host.sh")
  })

  cloudflare_tunnel_routes = {
    (var.api_hostname) = "http://localhost:8082"
    (var.ssh_hostname) = "ssh://localhost:22"
  }

  aiven_selected_plan = var.enable_aiven_postgresql ? try(one([
    for plan in data.aiven_service_plan_list.postgresql[0].service_plans : plan
    if plan.service_plan == var.aiven_plan
  ]), null) : null
  aiven_advertised_cloud_names = local.aiven_selected_plan == null ? [] : sort(keys(local.aiven_selected_plan.regions))
  aiven_available_cloud_names = [
    for cloud_name in local.aiven_advertised_cloud_names : cloud_name
    if startswith(cloud_name, "do-") || startswith(cloud_name, "upcloud-")
  ]
}

data "aiven_service_plan_list" "postgresql" {
  count = var.enable_aiven_postgresql ? 1 : 0

  project      = var.aiven_project_name
  service_type = "pg"

  lifecycle {
    postcondition {
      condition = try(
        length([
          for cloud_name in keys(one([
            for plan in self.service_plans : plan
            if plan.service_plan == var.aiven_plan
          ]).regions) : cloud_name
          if startswith(cloud_name, "do-") || startswith(cloud_name, "upcloud-")
        ]) > 0,
        false,
      )
      error_message = "The configured Aiven PostgreSQL plan must advertise at least one DigitalOcean or UpCloud region accepted for Free service creation."
    }

    postcondition {
      condition = var.aiven_cloud_name == null || try(
        contains([
          for cloud_name in keys(one([
            for plan in self.service_plans : plan
            if plan.service_plan == var.aiven_plan
          ]).regions) : cloud_name
          if startswith(cloud_name, "do-") || startswith(cloud_name, "upcloud-")
        ], var.aiven_cloud_name),
        false,
      )
      error_message = "The configured Aiven PostgreSQL cloud must be an advertised DigitalOcean or UpCloud region accepted for Free service creation."
    }
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
  cloud_init    = local.compose_host_cloud_init
  labels        = local.common_labels
}

module "postgresql" {
  count  = var.enable_aiven_postgresql ? 1 : 0
  source = "../../modules/aiven-postgresql"

  project_name          = var.aiven_project_name
  service_name          = var.aiven_service_name
  cloud_name            = var.aiven_cloud_name
  available_cloud_names = local.aiven_available_cloud_names
  plan                  = var.aiven_plan
  database_name         = var.aiven_database_name
  database_user_names   = var.aiven_database_user_names
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
  routes      = local.cloudflare_tunnel_routes
}
