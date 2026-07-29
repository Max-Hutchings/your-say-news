terraform {
  required_version = "~> 1.15.0"

  required_providers {
    aiven = {
      source  = "aiven/aiven"
      version = ">= 4.60.0, < 5.0.0"
    }
  }
}
