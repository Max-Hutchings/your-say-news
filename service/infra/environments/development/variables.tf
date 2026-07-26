variable "environment" {
  description = "Stable environment name used in resource names and labels."
  type        = string
  default     = "development"

  validation {
    condition     = var.environment == "development"
    error_message = "This Terraform root may only manage the development environment."
  }
}

variable "api_hostname" {
  description = "Public API hostname published through Cloudflare Tunnel."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$", var.api_hostname))
    error_message = "api_hostname must be a lower-case DNS hostname."
  }
}

variable "cloudflare_zone_name" {
  description = "Existing Cloudflare DNS zone containing api_hostname."
  type        = string
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

variable "aiven_project_name" {
  description = "Existing Aiven project in the approved Europe geographical area."
  type        = string
}

variable "aiven_service_name" {
  description = "Aiven PostgreSQL service name."
  type        = string
  default     = "your-say-news-development"
}

variable "r2_bucket_name" {
  description = "Private R2 media bucket created in the EU jurisdiction."
  type        = string
  default     = "your-say-news-development-media"
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
