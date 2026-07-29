resource "cloudflare_r2_bucket" "this" {
  account_id    = var.account_id
  name          = var.bucket_name
  jurisdiction  = var.jurisdiction
  location      = var.location
  storage_class = var.storage_class

  lifecycle {
    prevent_destroy = true
  }
}

resource "cloudflare_r2_bucket_cors" "this" {
  count = length(var.cors_rules) == 0 ? 0 : 1

  account_id   = var.account_id
  bucket_name  = cloudflare_r2_bucket.this.name
  jurisdiction = var.jurisdiction
  rules = [
    for rule in var.cors_rules : {
      id = rule.id
      allowed = {
        methods = rule.allowed_methods
        origins = rule.allowed_origins
        headers = rule.allowed_headers
      }
      expose_headers  = rule.expose_headers
      max_age_seconds = rule.max_age_seconds
    }
  ]
}

resource "cloudflare_r2_bucket_lifecycle" "this" {
  count = length(var.lifecycle_rules) == 0 ? 0 : 1

  account_id   = var.account_id
  bucket_name  = cloudflare_r2_bucket.this.name
  jurisdiction = var.jurisdiction
  rules        = var.lifecycle_rules
}
