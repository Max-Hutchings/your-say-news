mock_provider "aiven" {}
mock_provider "cloudflare" {}
mock_provider "grafana" {}
mock_provider "hcloud" {
  mock_data "hcloud_ssh_key" {
    defaults = {
      id = 1001
    }
  }

  mock_resource "hcloud_firewall" {
    defaults = {
      id = 2001
    }
  }

  mock_resource "hcloud_server" {
    defaults = {
      id = 3001
    }
  }
}

run "enabled_host_receives_the_reviewed_non_secret_bootstrap" {
  command = apply

  variables {
    enable_hcloud_host       = true
    enable_cloudflare_tunnel = true
    hcloud_ssh_key_names     = ["TheoHutchings908-your-say-news-development"]

    api_hostname          = "dev.yoursaynews.com"
    ssh_hostname          = "ssh-dev.yoursaynews.com"
    cloudflare_account_id = "9538d45e127bdb7d6b1bf1ecf9020146"
    cloudflare_zone_id    = "11111111111111111111111111111111"
    cloudflare_zone_name  = "yoursaynews.com"
    aiven_project_name    = "your-say-news-development"
  }

  assert {
    condition = alltrue([
      output.hcloud_host.name == "your-say-news-development",
      output.hcloud_host.cloud_init_sha256 == sha256(local.compose_host_cloud_init),
      output.hcloud_host.cloud_init_sha256 == "8718c6ee19a4e8b50d42997b339ca92950c4d636509692cfc526c698cb98ecbe",
    ])
    error_message = "The enabled host must publish its exact name and reviewed rendered-bootstrap fingerprint."
  }

  assert {
    condition = alltrue([
      output.hcloud_host.id == module.compose_host[0].server_id,
      output.hcloud_host.ipv4_address == module.compose_host[0].ipv4_address,
      output.hcloud_host.ipv6_address == module.compose_host[0].ipv6_address,
      output.hcloud_host.firewall_id == module.compose_host[0].firewall_id,
      output.cloudflare_tunnel.id == module.api_tunnel[0].tunnel_id,
      output.cloudflare_tunnel.hostnames == module.api_tunnel[0].route_hostnames,
      output.cloudflare_tunnel.routes == module.api_tunnel[0].route_services,
    ])
    error_message = "Environment outputs must map every host and Tunnel field from the enabled module instances."
  }

  assert {
    condition     = issensitive(output.cloudflare_tunnel_token)
    error_message = "The generated Cloudflare connector token must remain a sensitive root output."
  }

  assert {
    condition = alltrue([
      yamldecode(local.compose_host_cloud_init).ssh_pwauth == false,
      yamldecode(local.compose_host_cloud_init).disable_root == false,
      one([for user in yamldecode(local.compose_host_cloud_init).users : user if can(user.name)]).name == "deploy",
      one([for user in yamldecode(local.compose_host_cloud_init).users : user if can(user.name)]).lock_passwd == true,
    ])
    error_message = "Cloud-init must be valid YAML with a locked deployment account and no password SSH."
  }

  assert {
    condition = alltrue([
      one([for file in yamldecode(local.compose_host_cloud_init).write_files : file if file.path == "/usr/local/sbin/ysn-bootstrap-host"]).owner == "root:root",
      one([for file in yamldecode(local.compose_host_cloud_init).write_files : file if file.path == "/usr/local/sbin/ysn-bootstrap-host"]).permissions == "0750",
      jsonencode(yamldecode(local.compose_host_cloud_init).runcmd) == jsonencode([["bash", "/usr/local/sbin/ysn-bootstrap-host"]]),
    ])
    error_message = "Cloud-init must install the root-controlled bootstrap script and execute that exact script through runcmd."
  }

  assert {
    condition = alltrue([
      strcontains(local.compose_host_cloud_init, "PasswordAuthentication no"),
      strcontains(local.compose_host_cloud_init, "KbdInteractiveAuthentication no"),
      strcontains(local.compose_host_cloud_init, "PermitRootLogin prohibit-password"),
      strcontains(local.compose_host_cloud_init, "docker-compose-plugin"),
      strcontains(local.compose_host_cloud_init, "https://download.docker.com/linux/ubuntu"),
      strcontains(local.compose_host_cloud_init, "https://pkg.cloudflare.com/cloudflared"),
      strcontains(local.compose_host_cloud_init, "usermod --append --groups docker deploy"),
      strcontains(local.compose_host_cloud_init, "/root/.ssh/authorized_keys"),
      strcontains(local.compose_host_cloud_init, "-o deploy -g deploy /opt/your-say-news"),
      strcontains(local.compose_host_cloud_init, "ConditionPathExists=/etc/cloudflared/tunnel.env"),
      strcontains(local.compose_host_cloud_init, "User=cloudflared"),
      strcontains(local.compose_host_cloud_init, "Group=cloudflared"),
      strcontains(local.compose_host_cloud_init, "NoNewPrivileges=true"),
      strcontains(local.compose_host_cloud_init, "ProtectSystem=strict"),
      strcontains(local.compose_host_cloud_init, "systemctl enable cloudflared-ysn.service"),
      strcontains(local.compose_host_cloud_init, "OnCalendar=Sun *-*-* 04:00:00 Europe/London"),
      strcontains(local.compose_host_cloud_init, "systemctl enable --now docker.service containerd.service"),
      strcontains(local.compose_host_cloud_init, "ufw default deny incoming"),
      strcontains(local.compose_host_cloud_init, "ufw allow in on lo"),
      strcontains(local.compose_host_cloud_init, "ufw --force enable"),
      !strcontains(local.compose_host_cloud_init, "ufw allow 22/tcp"),
      strcontains(local.compose_host_cloud_init, "trap record_failure EXIT"),
      strcontains(local.compose_host_cloud_init, "rm -f \"$failure_marker\" \"$completion_marker\""),
      strcontains(local.compose_host_cloud_init, " > \"$completion_marker\""),
      strcontains(local.compose_host_cloud_init, " > \"$failure_marker\""),
    ])
    error_message = "Cloud-init must enforce SSH hardening, Docker/Compose, a non-root dormant Tunnel, deny-public-ingress and status evidence."
  }

  assert {
    condition = alltrue([
      !strcontains(local.compose_host_cloud_init, "TUNNEL_TOKEN="),
      !strcontains(local.compose_host_cloud_init, "DB_URL="),
      !strcontains(local.compose_host_cloud_init, "DB_REACTIVE_URL="),
      !strcontains(local.compose_host_cloud_init, "DB_USERNAME="),
      !strcontains(local.compose_host_cloud_init, "DB_PASSWORD="),
      !strcontains(local.compose_host_cloud_init, "S3_ACCESS_KEY_ID="),
      !strcontains(local.compose_host_cloud_init, "S3_SECRET_ACCESS_KEY="),
      !strcontains(local.compose_host_cloud_init, "XAI_API_KEY="),
      !strcontains(local.compose_host_cloud_init, "UNWRAPPED_API_KEY="),
      !strcontains(local.compose_host_cloud_init, "GRAFANA_CLOUD_OTLP_AUTHORIZATION="),
    ])
    error_message = "Cloud-init must never contain any runtime credential from the deployment contract."
  }

  assert {
    condition = output.cloudflare_tunnel.routes == tomap({
      "dev.yoursaynews.com"     = "http://localhost:8082"
      "ssh-dev.yoursaynews.com" = "ssh://localhost:22"
    })
    error_message = "The enabled host-level Tunnel must publish the exact loopback API and local SSH routes."
  }
}

run "compose_host_module_wires_bootstrap_and_safety_controls" {
  command = apply

  module {
    source = "../../modules/linux-compose-host"
  }

  variables {
    name          = "your-say-news-development"
    location      = "nbg1"
    server_type   = "cx23"
    image         = "ubuntu-24.04"
    ssh_key_names = ["development-break-glass"]
    ipv4_enabled  = true
    cloud_init    = "#cloud-config\nssh_pwauth: false\n"
    labels = {
      application = "your-say-news"
      environment = "development"
      managed_by  = "terraform"
    }
  }

  assert {
    condition = alltrue([
      hcloud_server.this.name == "your-say-news-development",
      hcloud_server.this.location == "nbg1",
      hcloud_server.this.server_type == "cx23",
      hcloud_server.this.image == "ubuntu-24.04",
      one(hcloud_server.this.public_net).ipv4_enabled == true,
      one(hcloud_server.this.public_net).ipv6_enabled == true,
      hcloud_server.this.backups == false,
      hcloud_server.this.delete_protection == true,
      hcloud_server.this.rebuild_protection == true,
      hcloud_server.this.labels == tomap({
        application = "your-say-news"
        environment = "development"
        managed_by  = "terraform"
      }),
    ])
    error_message = "The host resource must receive the exact image, location, size, key, networks, cloud-init and protection settings."
  }

  assert {
    condition = alltrue([
      hcloud_firewall.this.name == "your-say-news-development-deny-public-ingress",
      hcloud_firewall.this.labels == tomap({
        application = "your-say-news"
        environment = "development"
        managed_by  = "terraform"
      }),
      length(hcloud_firewall.this.rule) == 0,
    ])
    error_message = "The attached, labelled Hetzner firewall must retain implicit deny-all public ingress."
  }
}

run "compose_host_rejects_non_cloud_config_user_data" {
  command = plan

  module {
    source = "../../modules/linux-compose-host"
  }

  variables {
    name          = "your-say-news-development"
    location      = "nbg1"
    server_type   = "cx23"
    image         = "ubuntu-24.04"
    ssh_key_names = ["development-break-glass"]
    ipv4_enabled  = true
    cloud_init    = "#!/usr/bin/env bash\necho unsafe\n"
  }

  expect_failures = [var.cloud_init]
}

run "compose_host_rejects_near_miss_cloud_config_header" {
  command = plan

  module {
    source = "../../modules/linux-compose-host"
  }

  variables {
    name          = "your-say-news-development"
    location      = "nbg1"
    server_type   = "cx23"
    image         = "ubuntu-24.04"
    ssh_key_names = ["development-break-glass"]
    ipv4_enabled  = true
    cloud_init    = "#cloud-configuration\nssh_pwauth: false\n"
  }

  expect_failures = [var.cloud_init]
}

run "compose_host_rejects_missing_break_glass_key" {
  command = plan

  module {
    source = "../../modules/linux-compose-host"
  }

  variables {
    name          = "your-say-news-development"
    location      = "nbg1"
    server_type   = "cx23"
    image         = "ubuntu-24.04"
    ssh_key_names = []
    ipv4_enabled  = true
    cloud_init    = "#cloud-config\nssh_pwauth: false\n"
  }

  expect_failures = [var.ssh_key_names]
}

run "compose_host_honours_the_no_paid_ipv4_boundary" {
  command = plan

  module {
    source = "../../modules/linux-compose-host"
  }

  variables {
    name          = "your-say-news-development"
    location      = "nbg1"
    server_type   = "cx23"
    image         = "ubuntu-24.04"
    ssh_key_names = ["development-break-glass"]
    ipv4_enabled  = false
    cloud_init    = "#cloud-config\nssh_pwauth: false\n"
  }

  assert {
    condition     = one(hcloud_server.this.public_net).ipv4_enabled == false
    error_message = "The module must not purchase or attach IPv4 when the environment explicitly disables it."
  }
}

run "tunnel_module_builds_the_proxied_dns_and_fallback_contract" {
  command = apply

  module {
    source = "../../modules/cloudflare-api-tunnel"
  }

  variables {
    account_id  = "9538d45e127bdb7d6b1bf1ecf9020146"
    zone_id     = "11111111111111111111111111111111"
    tunnel_name = "your-say-news-development"
    routes = {
      "dev.yoursaynews.com"     = "http://localhost:8082"
      "ssh-dev.yoursaynews.com" = "ssh://localhost:22"
    }
  }

  assert {
    condition = alltrue([
      length(cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress) == 3,
      cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress[0].hostname == "dev.yoursaynews.com",
      cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress[0].service == "http://localhost:8082",
      cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress[1].hostname == "ssh-dev.yoursaynews.com",
      cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress[1].service == "ssh://localhost:22",
      cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress[2].hostname == null,
      cloudflare_zero_trust_tunnel_cloudflared_config.this.config.ingress[2].service == "http_status:404",
    ])
    error_message = "The remotely managed Tunnel must retain both sorted routes and the final 404 fallback."
  }

  assert {
    condition = alltrue([
      cloudflare_dns_record.route["dev.yoursaynews.com"].name == "dev.yoursaynews.com",
      cloudflare_dns_record.route["dev.yoursaynews.com"].type == "CNAME",
      cloudflare_dns_record.route["dev.yoursaynews.com"].content == "${cloudflare_zero_trust_tunnel_cloudflared.this.id}.cfargotunnel.com",
      cloudflare_dns_record.route["dev.yoursaynews.com"].ttl == 1,
      cloudflare_dns_record.route["dev.yoursaynews.com"].proxied == true,
      cloudflare_dns_record.route["ssh-dev.yoursaynews.com"].name == "ssh-dev.yoursaynews.com",
      cloudflare_dns_record.route["ssh-dev.yoursaynews.com"].type == "CNAME",
      cloudflare_dns_record.route["ssh-dev.yoursaynews.com"].content == "${cloudflare_zero_trust_tunnel_cloudflared.this.id}.cfargotunnel.com",
      cloudflare_dns_record.route["ssh-dev.yoursaynews.com"].ttl == 1,
      cloudflare_dns_record.route["ssh-dev.yoursaynews.com"].proxied == true,
    ])
    error_message = "Every Tunnel hostname must use a proxied automatic-TTL CNAME to the exact Tunnel UUID."
  }

  assert {
    condition     = issensitive(output.tunnel_token)
    error_message = "The module connector token must remain sensitive."
  }
}
