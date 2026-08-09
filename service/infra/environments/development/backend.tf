terraform {
  required_version = "~> 1.15.0"

  backend "remote" {}

  required_providers {
    aiven = {
      source  = "aiven/aiven"
      version = "4.60.0"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "5.22.0"
    }
    grafana = {
      source  = "grafana/grafana"
      version = "4.40.1"
    }
    hcloud = {
      source  = "hetznercloud/hcloud"
      version = "1.66.1"
    }
  }
}
