#!/usr/bin/env bash
set -euo pipefail

readonly status_directory=/var/lib/your-say-news
readonly failure_marker="${status_directory}/bootstrap-failed"
readonly completion_marker="${status_directory}/bootstrap-complete"

install -d -m 0755 "$status_directory"
rm -f "$failure_marker" "$completion_marker"

record_failure() {
  local exit_code=$?

  if (( exit_code != 0 )); then
    printf 'Host bootstrap failed at %s with exit code %s\n' \
      "$(date --iso-8601=seconds)" "$exit_code" > "$failure_marker"
  fi
}
trap record_failure EXIT

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install --yes ca-certificates curl gnupg unattended-upgrades ufw

# Install Docker Engine and Compose from Docker's signed Ubuntu repository.
install -m 0755 -d /etc/apt/keyrings
curl --fail --silent --show-error --location \
  https://download.docker.com/linux/ubuntu/gpg \
  --output /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

# shellcheck disable=SC1091
. /etc/os-release
cat > /etc/apt/sources.list.d/docker.sources <<DOCKER_REPOSITORY
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
DOCKER_REPOSITORY

# Install cloudflared from Cloudflare's signed Ubuntu repository. The connector remains disabled
# until its token is delivered separately through the documented bootstrap procedure.
install -m 0755 -d /usr/share/keyrings
curl --fail --silent --show-error --location \
  https://pkg.cloudflare.com/cloudflare-main.gpg \
  --output /usr/share/keyrings/cloudflare-main.gpg
cat > /etc/apt/sources.list.d/cloudflared.list <<'CLOUDFLARE_REPOSITORY'
deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared noble main
CLOUDFLARE_REPOSITORY

apt-get update
apt-get install --yes \
  cloudflared \
  containerd.io \
  docker-buildx-plugin \
  docker-ce \
  docker-ce-cli \
  docker-compose-plugin

systemctl enable --now docker.service containerd.service
usermod --append --groups docker deploy

if ! getent group cloudflared >/dev/null 2>&1; then
  groupadd --system cloudflared
fi
if ! id cloudflared >/dev/null 2>&1; then
  useradd --system --gid cloudflared --home-dir /var/lib/cloudflared \
    --shell /usr/sbin/nologin cloudflared
else
  usermod --gid cloudflared cloudflared
fi

# Hetzner injects the selected break-glass keys for root. Copy the same public keys to the
# unprivileged deployment account without placing key material in Terraform variables.
install -d -m 0700 -o deploy -g deploy /home/deploy/.ssh
install -m 0600 -o deploy -g deploy \
  /root/.ssh/authorized_keys \
  /home/deploy/.ssh/authorized_keys

install -d -m 0750 -o deploy -g deploy /opt/your-say-news
install -d -m 0750 -o root -g cloudflared /etc/cloudflared
install -d -m 0750 -o cloudflared -g cloudflared /var/lib/cloudflared

sshd -t
systemctl reload ssh.service

# Provider and host firewalls both deny public ingress. Loopback remains available to the local
# Tunnel connector and the API continues to bind only to 127.0.0.1.
ufw default deny incoming
ufw default allow outgoing
ufw allow in on lo
ufw --force enable

systemctl daemon-reload
systemctl enable cloudflared-ysn.service
systemctl restart apt-daily-upgrade.timer

docker version
docker compose version
cloudflared --version

printf 'Host bootstrap completed at %s\n' "$(date --iso-8601=seconds)" > "$completion_marker"
