# Provider credentials are read from their standard environment variables by CI. Do not add
# tokens, passwords or generated credentials to variables, tfvars, outputs or Terraform state.
provider "aiven" {}

provider "cloudflare" {}

provider "grafana" {}

provider "hcloud" {}
