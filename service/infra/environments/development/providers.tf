# Provider authentication credentials are read from their standard environment variables by CI.
# Do not add tokens or passwords to variables/tfvars. Provider-generated resource credentials can
# still enter remote state, so HCP state must be treated as secret.
provider "aiven" {}

provider "cloudflare" {}

provider "grafana" {}

provider "hcloud" {}
