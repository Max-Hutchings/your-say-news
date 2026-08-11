variable "account_id" {
  description = "Cloudflare account that owns the R2 bucket."
  type        = string
}

variable "bucket_name" {
  description = "Globally unique R2 bucket name."
  type        = string
}

variable "jurisdiction" {
  description = "Immutable R2 jurisdiction."
  type        = string
  default     = "eu"

  validation {
    condition     = var.jurisdiction == "eu"
    error_message = "Your Say News development buckets must use the immutable eu jurisdiction."
  }
}

variable "location" {
  description = "Best-effort R2 placement hint within the selected jurisdiction."
  type        = string
  default     = "weur"
}

variable "storage_class" {
  description = "Default R2 storage class."
  type        = string
  default     = "Standard"

  validation {
    condition     = var.storage_class == "Standard"
    error_message = "The free R2 allowance applies only to Standard storage."
  }
}

variable "cors_rules" {
  description = "Optional browser CORS rules. Keep empty for the private backup bucket."
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

variable "lifecycle_rules" {
  description = "Cloudflare R2 lifecycle rule objects. Their values belong in the environment root."
  type        = any
  default     = []
}
