output "tunnel_id" {
  description = "Cloudflare Tunnel UUID."
  value       = cloudflare_zero_trust_tunnel_cloudflared.this.id
}

output "route_hostnames" {
  description = "Hostnames routed through the Tunnel."
  value       = sort(keys(var.routes))
}

output "tunnel_token" {
  description = "Sensitive connector token consumed only by the deployment secret workflow."
  value       = data.cloudflare_zero_trust_tunnel_cloudflared_token.this.token
  sensitive   = true
}
