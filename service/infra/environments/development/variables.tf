variable "environment" {
  description = "Stable environment name used in resource names and labels."
  type        = string
  default     = "development"

  validation {
    condition     = var.environment == "development"
    error_message = "This Terraform root may only manage the development environment."
  }
}

# Resource switches stay false until the corresponding values and provider credentials are ready.
variable "enable_hcloud_host" {
  description = "Create the Hetzner Compose host."
  type        = bool
  default     = false
}

variable "enable_aiven_postgresql" {
  description = "Create the Aiven PostgreSQL service, database and service users."
  type        = bool
  default     = false
}

variable "enable_r2_buckets" {
  description = "Create the Cloudflare R2 media and backup buckets."
  type        = bool
  default     = false
}

variable "enable_cloudflare_tunnel" {
  description = "Create Cloudflare Tunnel configuration and DNS routes."
  type        = bool
  default     = false
}

# Cloudflare and public hostnames.
variable "cloudflare_account_id" {
  description = "Cloudflare account ID shown on the account overview page."
  type        = string
  default     = ""

  validation {
    condition = (
      (!var.enable_r2_buckets && !var.enable_cloudflare_tunnel && var.cloudflare_account_id == "") ||
      can(regex("^[0-9a-f]{32}$", var.cloudflare_account_id))
    )
    error_message = "Set a 32-character Cloudflare account ID before enabling R2 or Tunnel."
  }
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone ID for yoursaynews.com."
  type        = string
  default     = ""

  validation {
    condition = (
      (!var.enable_cloudflare_tunnel && var.cloudflare_zone_id == "") ||
      can(regex("^[0-9a-f]{32}$", var.cloudflare_zone_id))
    )
    error_message = "Set a 32-character Cloudflare zone ID before enabling Tunnel."
  }
}

variable "cloudflare_zone_name" {
  description = "Existing Cloudflare DNS zone containing the development hostnames."
  type        = string
}

variable "api_hostname" {
  description = "Public API hostname published through Cloudflare Tunnel."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$", var.api_hostname))
    error_message = "api_hostname must be a lower-case DNS hostname."
  }
}

variable "ssh_hostname" {
  description = "Cloudflare Access-protected SSH hostname."
  type        = string
  default     = "ssh-dev.yoursaynews.com"
}

variable "cloudflare_tunnel_name" {
  description = "Cloudflare Tunnel name."
  type        = string
  default     = "your-say-news-development"
}

# Hetzner.
variable "hcloud_server_name" {
  description = "Hetzner server hostname."
  type        = string
  default     = "your-say-news-development"
}

variable "hcloud_location" {
  description = "Hetzner EU location for the single Compose host."
  type        = string
  default     = "nbg1"

  validation {
    condition     = contains(["nbg1", "fsn1"], var.hcloud_location)
    error_message = "Use the approved Nuremberg (nbg1) or Falkenstein (fsn1) location."
  }
}

variable "hcloud_server_type" {
  description = "Hetzner server type within the development budget."
  type        = string
  default     = "cx23"
}

variable "hcloud_image" {
  description = "Hetzner Ubuntu image."
  type        = string
  default     = "ubuntu-24.04"
}

variable "hcloud_ssh_key_names" {
  description = "Existing Hetzner SSH key names for break-glass recovery."
  type        = set(string)
  default     = []

  validation {
    condition     = !var.enable_hcloud_host || length(var.hcloud_ssh_key_names) > 0
    error_message = "At least one existing Hetzner SSH key is required when the host is enabled."
  }
}

variable "hcloud_ipv4_enabled" {
  description = "Whether to purchase/attach primary IPv4 to the Hetzner server."
  type        = bool
  default     = true
}

# Aiven.
variable "aiven_project_name" {
  description = "Existing Aiven project name."
  type        = string

  validation {
    condition     = !var.enable_aiven_postgresql || length(trimspace(var.aiven_project_name)) > 0
    error_message = "Set the Aiven project name before enabling PostgreSQL."
  }
}

variable "aiven_service_name" {
  description = "Aiven PostgreSQL service name."
  type        = string
  default     = "your-say-news-development"
}

variable "aiven_cloud_name" {
  description = "Optional Aiven cloud identifier. Null lets Aiven assign the Free-tier provider and region."
  type        = string
  default     = null
}

variable "aiven_plan" {
  description = "Exact Aiven PostgreSQL plan identifier."
  type        = string
  default     = ""

  validation {
    condition     = !var.enable_aiven_postgresql || length(trimspace(var.aiven_plan)) > 0
    error_message = "Set the exact Aiven plan identifier before enabling PostgreSQL."
  }
}

variable "aiven_database_name" {
  description = "Application database name."
  type        = string
  default     = "your_say_news"
}

variable "aiven_database_user_names" {
  description = "Aiven service users created for runtime and migrations."
  type        = set(string)
  default = [
    "ysn_migration",
    "ysn_runtime",
  ]
}

# R2.
variable "r2_media_bucket_name" {
  description = "Private R2 media bucket created in the EU jurisdiction."
  type        = string
  default     = "your-say-news-media-development"
}

variable "r2_backup_bucket_name" {
  description = "Private R2 database-backup bucket created in the EU jurisdiction."
  type        = string
  default     = "your-say-news-backup-development"
}

variable "r2_media_cors_rules" {
  description = "Browser CORS rules for direct presigned media operations."
  type = list(object({
    id              = string
    allowed_methods = list(string)
    allowed_origins = list(string)
    allowed_headers = optional(list(string), [])
    expose_headers  = optional(list(string), [])
    max_age_seconds = optional(number, 3600)
  }))
  default = []
}

variable "r2_media_lifecycle_rules" {
  description = "Provider-shaped lifecycle rules for abandoned media uploads."
  type        = any
  default     = []
}

variable "r2_backup_lifecycle_rules" {
  description = "Provider-shaped retention rules for prefixed daily/monthly database backups."
  type        = any
  default     = []
}

variable "monthly_budget_gbp" {
  description = "Operational budget guardrail documented by the infrastructure plan."
  type        = number
  default     = 20

  validation {
    condition     = var.monthly_budget_gbp > 0 && var.monthly_budget_gbp <= 20
    error_message = "The approved development infrastructure budget is at most GBP 20/month."
  }
}
