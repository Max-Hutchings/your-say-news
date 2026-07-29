output "service_name" {
  description = "Aiven PostgreSQL service name."
  value       = aiven_pg.this.service_name
}

output "cloud_name" {
  description = "Cloud provider and region assigned to the PostgreSQL service."
  value       = aiven_pg.this.cloud_name
}

output "service_host" {
  description = "Aiven PostgreSQL TLS hostname."
  value       = aiven_pg.this.service_host
}

output "service_port" {
  description = "Aiven PostgreSQL TLS port."
  value       = aiven_pg.this.service_port
}

output "database_name" {
  description = "Application database name."
  value       = aiven_pg_database.application.database_name
}

output "database_user_names" {
  description = "Application service-user names. Passwords are deliberately not output."
  value       = sort([for user in aiven_pg_user.application : user.username])
}
