resource "aiven_pg" "this" {
  project                = var.project_name
  service_name           = var.service_name
  cloud_name             = var.cloud_name
  plan                   = var.plan
  termination_protection = var.termination_protection

  pg_user_config {
    enable_ipv6 = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aiven_pg_database" "application" {
  project       = var.project_name
  service_name  = aiven_pg.this.service_name
  database_name = var.database_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aiven_pg_user" "application" {
  for_each = var.database_user_names

  project      = var.project_name
  service_name = aiven_pg.this.service_name
  username     = each.value
}
