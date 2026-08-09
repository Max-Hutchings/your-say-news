# Development-specific, non-secret inputs.
#
# Fill the blank values below, then enable one resource group at a time. Terraform validation and
# provider credentials are completed before changing any enable_* value to true.
#
# Never put API tokens, passwords, R2 access keys or connector tokens in this file.

enable_hcloud_host       = true
enable_aiven_postgresql  = true
enable_r2_buckets        = true
enable_cloudflare_tunnel = true

# Cloudflare dashboard -> account/zone overview.
cloudflare_account_id = "9538d45e127bdb7d6b1bf1ecf9020146"
cloudflare_zone_id    = "9d9a9700dea3507c8af078cf7ff839c5"
cloudflare_zone_name  = "yoursaynews.com"

api_hostname             = "dev.yoursaynews.com"
ssh_hostname             = "ssh-dev.yoursaynews.com"
cloudflare_tunnel_name   = "your-say-news-development"
r2_media_bucket_name     = "your-say-news-media-development"
r2_backup_bucket_name    = "your-say-news-backup-development"
r2_media_cors_rules      = [] # Android uses presigned URLs directly; no browser origin is allowed.
r2_media_lifecycle_rules = [] # Keep R2's default seven-day incomplete multipart cleanup.
r2_backup_lifecycle_rules = [
  {
    id         = "abort-incomplete-backup-uploads-after-7-days"
    conditions = { prefix = "" }
    enabled    = true
    abort_multipart_uploads_transition = {
      condition = {
        max_age = 604800
        type    = "Age"
      }
    }
  },
  {
    id         = "expire-daily-backups-after-7-days"
    conditions = { prefix = "daily/" }
    enabled    = true
    delete_objects_transition = {
      condition = {
        max_age = 604800
        type    = "Age"
      }
    }
  },
  {
    id         = "expire-monthly-backups-after-90-days"
    conditions = { prefix = "monthly/" }
    enabled    = true
    delete_objects_transition = {
      condition = {
        max_age = 7776000
        type    = "Age"
      }
    }
  },
]

# Hetzner Cloud project.
hcloud_location    = "nbg1"
hcloud_server_type = "cx23"
hcloud_image       = "ubuntu-24.04"
hcloud_ssh_key_names = [
  "TheoHutchings908-your-say-news-development",
]
hcloud_ipv4_enabled = false # IPv6-only host; avoids the paid primary IPv4 charge.

# Aiven project/service options.
aiven_project_name        = "your-say-news-development"
aiven_service_name        = "your-say-news-development"
aiven_cloud_name          = null # Aiven assigns the Free-tier provider and region.
aiven_plan                = "free-1-1gb"
aiven_database_name       = "your_say_news"
aiven_database_user_names = ["ysn_migration", "ysn_runtime"]

monthly_budget_gbp = 20
