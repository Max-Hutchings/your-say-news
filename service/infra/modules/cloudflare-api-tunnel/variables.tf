variable "account_id" {
  description = "Cloudflare account that owns the Tunnel."
  type        = string
}

variable "zone_id" {
  description = "Cloudflare DNS zone in which route records are created."
  type        = string
}

variable "tunnel_name" {
  description = "Unique Cloudflare Tunnel name."
  type        = string
}

variable "routes" {
  description = "Map of public hostname to the origin service URL understood by cloudflared."
  type        = map(string)

  validation {
    condition     = length(var.routes) > 0
    error_message = "At least one Cloudflare Tunnel route is required."
  }
}
