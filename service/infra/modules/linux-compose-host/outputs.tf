output "server_id" {
  description = "Hetzner server identifier."
  value       = hcloud_server.this.id
}

output "server_name" {
  description = "Hetzner server name."
  value       = hcloud_server.this.name
}

output "ipv4_address" {
  description = "Public IPv4 address, or an empty value when IPv4 is disabled."
  value       = hcloud_server.this.ipv4_address
}

output "ipv6_address" {
  description = "Public IPv6 address."
  value       = hcloud_server.this.ipv6_address
}

output "firewall_id" {
  description = "Deny-public-ingress firewall identifier."
  value       = hcloud_firewall.this.id
}

output "cloud_init_sha256" {
  description = "SHA-256 fingerprint of the non-secret first-boot host configuration."
  value       = nonsensitive(sha256(hcloud_server.this.user_data))
}
