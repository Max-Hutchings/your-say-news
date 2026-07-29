variable "name" {
  description = "Unique Hetzner server and firewall name."
  type        = string
}

variable "location" {
  description = "Hetzner location in which to create the server."
  type        = string
}

variable "server_type" {
  description = "Hetzner server type, for example cx23."
  type        = string
}

variable "image" {
  description = "Hetzner operating-system image."
  type        = string
  default     = "ubuntu-24.04"
}

variable "ssh_key_names" {
  description = "Names of existing Hetzner SSH keys injected for break-glass recovery."
  type        = set(string)
}

variable "ipv4_enabled" {
  description = "Whether the server receives a paid public primary IPv4 address."
  type        = bool
}

variable "ipv6_enabled" {
  description = "Whether the server receives a public primary IPv6 network."
  type        = bool
  default     = true
}

variable "cloud_init" {
  description = "Optional non-secret cloud-init document. Never include runtime credentials."
  type        = string
  default     = null
}

variable "labels" {
  description = "Low-cardinality labels applied to the server and firewall."
  type        = map(string)
  default     = {}
}
