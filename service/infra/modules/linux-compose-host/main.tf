data "hcloud_ssh_key" "break_glass" {
  for_each = var.ssh_key_names
  name     = each.value
}

# With no inbound rules Hetzner applies an implicit deny-all policy. With no outbound rules all
# outbound traffic remains available for package repositories, Cloudflare Tunnel and managed APIs.
resource "hcloud_firewall" "this" {
  name   = "${var.name}-deny-public-ingress"
  labels = var.labels
}

resource "hcloud_server" "this" {
  name        = var.name
  location    = var.location
  server_type = var.server_type
  image       = var.image

  ssh_keys = [for key in data.hcloud_ssh_key.break_glass : key.id]
  firewall_ids = [
    hcloud_firewall.this.id,
  ]

  public_net {
    ipv4_enabled = var.ipv4_enabled
    ipv6_enabled = var.ipv6_enabled
  }

  user_data          = var.cloud_init
  backups            = false
  delete_protection  = true
  rebuild_protection = true
  labels             = var.labels

  lifecycle {
    prevent_destroy = true
  }
}
