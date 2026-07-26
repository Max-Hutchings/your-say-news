# Aiven PostgreSQL module

Owns the development PostgreSQL service, application database/user boundary, connection settings
and termination protection in the approved Europe geographical area.

Generated credentials and connection URIs are sensitive Terraform state. The module must expose
only sensitive outputs and must prevent service replacement/deletion in the normal apply workflow.
