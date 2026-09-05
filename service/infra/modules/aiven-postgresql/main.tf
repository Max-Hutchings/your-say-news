locals {
  resolved_cloud_name = var.cloud_name != null ? var.cloud_name : try(
    sort(tolist(var.available_cloud_names))[0],
    null,
  )
}

resource "aiven_pg" "this" {
  project                = var.project_name
  service_name           = var.service_name
  cloud_name             = local.resolved_cloud_name
  plan                   = var.plan
  termination_protection = var.termination_protection

  lifecycle {
    prevent_destroy = true

    precondition {
      condition     = local.resolved_cloud_name != null
      error_message = "The Aiven PostgreSQL plan must advertise at least one available cloud when cloud_name is unset."
    }

    precondition {
      condition     = var.cloud_name == null || contains(var.available_cloud_names, var.cloud_name)
      error_message = "The configured Aiven PostgreSQL cloud must be advertised for the selected plan."
    }
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
