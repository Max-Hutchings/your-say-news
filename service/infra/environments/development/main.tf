locals {
  common_labels = {
    application = "your-say-news"
    environment = var.environment
    managed_by  = "terraform"
  }
}

# Phase 2 adds repository-local module calls from ../../modules here. Keeping the environment root
# resource-free for now prevents an incomplete skeleton from provisioning a VM, database, bucket
# or tunnel without deletion protection and reviewed provider account settings.
