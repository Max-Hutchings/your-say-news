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

  validation {
    condition     = length(var.ssh_key_names) > 0
    error_message = "At least one existing Hetzner SSH key is required for break-glass recovery."
  }
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
  description = "Non-secret cloud-init document used to prepare the host for deployment. Never include runtime credentials."
  type        = string

  validation {
    condition     = can(regex("^#cloud-config(?:\\r?\\n|$)", var.cloud_init))
    error_message = "cloud_init must begin with the exact #cloud-config header."
  }
}

variable "labels" {
  description = "Low-cardinality labels applied to the server and firewall."
  type        = map(string)
  default     = {}
}
