mock_provider "aiven" {}
mock_provider "cloudflare" {}
mock_provider "grafana" {}
mock_provider "hcloud" {}

run "all_resource_groups_are_disabled_by_default" {
  command = plan

  variables {
    api_hostname         = "dev.yoursaynews.com"
    cloudflare_zone_name = "yoursaynews.com"
    aiven_project_name   = "your-say-news-development"
  }

  assert {
    condition     = output.hcloud_host == null
    error_message = "The Hetzner host must not be planned until explicitly enabled."
  }

  assert {
    condition     = output.postgresql == null
    error_message = "Aiven PostgreSQL must not be planned until explicitly enabled."
  }

  assert {
    condition     = output.r2 == null
    error_message = "R2 buckets must not be planned until explicitly enabled."
  }

  assert {
    condition     = output.cloudflare_tunnel == null
    error_message = "Cloudflare Tunnel must not be planned until explicitly enabled."
  }

  assert {
    condition = alltrue([
      length(module.compose_host) == 0,
      length(module.postgresql) == 0,
      length(module.media_bucket) == 0,
      length(module.backup_bucket) == 0,
      length(module.api_tunnel) == 0,
    ])
    error_message = "Disabled resource-group switches must create no module instances."
  }
}
