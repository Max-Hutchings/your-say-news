# Terraform modules

These modules are repository-local while Your Say News is their only consumer. Environment roots
reference them with relative paths and own provider configuration.

Every implemented module must:

- have a narrow provider-specific responsibility;
- document typed inputs, outputs and sensitive state;
- enable provider deletion/termination protection where available;
- add Terraform `prevent_destroy` to the VM, database, bucket and tunnel where appropriate;
- avoid provisioners for routine host/application deployment; and
- include a plan fixture or test that demonstrates destructive changes are rejected.

Extract stable modules to a versioned platform repository only when a second application repository
needs them.
