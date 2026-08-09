output "bucket_name" {
  description = "R2 bucket name."
  value       = cloudflare_r2_bucket.this.name
}

output "jurisdiction" {
  description = "Immutable R2 jurisdiction."
  value       = cloudflare_r2_bucket.this.jurisdiction
}

output "s3_endpoint" {
  description = "Jurisdiction-specific S3 endpoint for this account."
  value       = "https://${var.account_id}.${var.jurisdiction}.r2.cloudflarestorage.com"
}
