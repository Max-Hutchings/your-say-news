# DevOps travel handoff

Date: 2026-09-05  
Repository: `Max-Hutchings/your-say-news`  
Branch: `feat/add-infra`  
Current commit when this handoff was written: `0517c6f`

This is a portable continuation note for the development infrastructure work. It contains no
credential values, private keys, Tunnel tokens or user identifiers beyond repository/provider
resource names already committed to Git.

## Confirmed project state

- `main` has been merged into `feat/add-infra`.
- Development Terraform has not been applied yet.
- Production is intentionally not ready and must remain a placeholder.
- The development architecture uses:
  - Hetzner CX23 in `nbg1`, IPv6-only;
  - Aiven PostgreSQL Free;
  - Cloudflare R2 for media and database backups;
  - a Cloudflare Tunnel for the public API and private SSH;
  - HCP Terraform for state; and
  - GitHub Actions for plan, explicit manual apply and application deployment.
- Cloudflare account/zone/Access preparation was reported as already completed externally.
- Google Play Console has now been created and verified. The next external mobile work is linking
  the Play application and Firebase Android application.
- Firebase Emulator authentication exists locally. Hosted Firebase authentication and the backend
  Firebase deployment contract are still application work.
- Google Play/Firebase work does not block the first infrastructure plan or apply.

## Network and identity boundaries

Google Play does not connect directly to Cloudflare.

```text
Google Play distributes the Android app
                    |
                    v
Android app signs in through Firebase
                    |
                    v
Android app calls https://dev.yoursaynews.com/api with a Firebase ID token
                    |
                    v
Cloudflare Tunnel routes the HTTPS request to localhost:8082 on the VM
                    |
                    v
post-service verifies the Firebase token and applies database authorization
```

Terraform manages the development Tunnel, its two DNS records and the R2 buckets. It does not
create production infrastructure. The future production apex `yoursaynews.com` remains separate
and untouched.

## SSH credentials

These are separate credentials:

- `HCLOUD_TOKEN` lets Terraform use the Hetzner API and create infrastructure.
- `DEV_SSH_PRIVATE_KEY` lets the application deployment workflow SSH to the completed VM as
  `deploy` through Cloudflare Access.
- `DEV_SSH_KNOWN_HOSTS` pins the new VM's SSH host identity and can only be populated after the VM
  exists.

The original Hetzner public-key registration exists under:

```text
TheoHutchings908-your-say-news-development
```

A new dedicated GitHub Actions key pair was generated locally:

```text
Private: ~/.ssh/your-say-news-development-deploy
Public:  ~/.ssh/your-say-news-development-deploy.pub
Hetzner key name: github-actions-your-say-news-development
```

The private key is data, not an executable. Never run it, commit it or paste it into chat. Its
complete contents belong only in the GitHub repository secret `DEV_SSH_PRIVATE_KEY`. The public
key belongs in Hetzner.

Before applying, confirm the new public key has been added to the Hetzner project with the exact
name above. Then ensure the saved repository file contains both Hetzner key names:

```hcl
hcloud_ssh_key_names = [
  "TheoHutchings908-your-say-news-development",
  "github-actions-your-say-news-development",
]
```

Important: when this handoff was written, the on-disk `development.tfvars` still contained only
the original Theo key, even though the editor showed both keys selected. Save the editor buffer,
then verify with `git diff` before committing.

## GitHub secrets observed in the latest screenshot

The screenshot showed these repository secrets:

- `AIVEN_TOKEN`
- `CLOUDFLARE_API_TOKEN`
- `DEV_DB_MIGRATION_USERNAME`
- `DEV_DB_USERNAME`
- `DEV_GRAFANA_CLOUD_OTLP_AUTHORIZATION`
- `DEV_GRAFANA_CLOUD_OTLP_ENDPOINT`
- `DEV_SSH_PRIVATE_KEY`
- `HCLOUD_TOKEN`
- `TF_API_TOKEN`

The infrastructure plan/apply requires HCP Terraform, Aiven, Hetzner and Cloudflare provider
credentials. Application deployment later requires additional values, including
`DEV_SSH_KNOWN_HOSTS`, database URLs/passwords, Cloudflare Access service-token credentials, R2
credentials, authentication configuration and the selected AI-provider key.

Do not record secret values in this file.

## Install and test Terraform locally

The project pins Terraform `1.15.8`; its `required_version` accepts `1.15.x`, not `1.16.x`.
Ubuntu's default package sources do not include Terraform, so add HashiCorp's repository:

```bash
sudo apt-get update
sudo apt-get install -y wget gpg

wget -O- https://apt.releases.hashicorp.com/gpg \
  | gpg --dearmor \
  | sudo tee /usr/share/keyrings/hashicorp-archive-keyring.gpg >/dev/null

echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(grep -oP '(?<=UBUNTU_CODENAME=).*' /etc/os-release || lsb_release -cs) main" \
  | sudo tee /etc/apt/sources.list.d/hashicorp.list

sudo apt-get update
apt-cache madison terraform | grep 1.15.8
sudo apt-get install -y terraform=1.15.8-1
terraform version
```

From the repository root, run the checks matching CI:

```bash
terraform fmt -check -recursive -diff service/infra

cd service/infra/environments/development
terraform init -backend=false
terraform validate -no-color
terraform test -no-color
```

If formatting fails:

```bash
terraform fmt -recursive service/infra
git diff
```

Use GitHub Actions for the authenticated plan; do not move provider-token values into local
Terraform variables.

## Commit, plan and apply

1. Save `development.tfvars` with both Hetzner SSH key names.
2. Confirm the new public key exists in Hetzner with the exact matching name.
3. Run the local checks above.
4. Review `git diff` and ensure no credentials are present.
5. Commit and push `feat/add-infra`.
6. The `Infrastructure Development` push workflow runs Format, Validate/Test and Plan.
7. Review the plan. The first real plan should be creation-only; investigate unexpected updates,
   deletes or replacements.
8. Copy the successful plan run ID and its full 40-character commit SHA.
9. Run the same workflow manually from the exact same branch/commit.
10. Enter the run ID, full commit SHA and exact confirmation phrase:

```text
apply development
```

The saved-plan artifact expires after one day. A newer push changes the branch head and requires a
new plan.

## What Terraform creates

- Hetzner VM and deny-public-ingress firewall.
- Aiven PostgreSQL service, database, migration user and runtime user.
- EU R2 media and backup buckets with reviewed lifecycle rules.
- Cloudflare Tunnel and connector token.
- Proxied DNS routes:
  - `dev.yoursaynews.com` to `http://localhost:8082`;
  - `ssh-dev.yoursaynews.com` to `ssh://localhost:22`.

Cloud-init automatically:

- creates the locked `deploy` user;
- installs selected Hetzner public keys for root and `deploy`;
- disables password SSH;
- installs Docker Engine and Docker Compose;
- installs `cloudflared`;
- configures unattended updates;
- creates `/opt/your-say-news`;
- enables the dormant Tunnel service; and
- enables a host firewall with no public inbound ports.

## One-time Hetzner console work after apply

Use the trusted Hetzner web console as root. First verify cloud-init:

```bash
cloud-init status --wait
cat /var/lib/your-say-news/bootstrap-complete
test ! -e /var/lib/your-say-news/bootstrap-failed
```

Retrieve the sensitive `cloudflare_tunnel_token` from the protected Terraform output and deliver
it through the console without placing it in Git, cloud-init or shell arguments:

```bash
install -o root -g cloudflared -m 0640 /dev/null /etc/cloudflared/tunnel.env
read -rsp "Tunnel token: " YSN_TUNNEL_BOOTSTRAP_TOKEN
printf '\n'
printf 'TUNNEL_TOKEN=%s\n' "$YSN_TUNNEL_BOOTSTRAP_TOKEN" > /etc/cloudflared/tunnel.env
unset YSN_TUNNEL_BOOTSTRAP_TOKEN
systemctl start cloudflared-ysn.service
systemctl status --no-pager cloudflared-ysn.service
```

Then capture the server host key through the trusted console and store the complete known-hosts
line in the GitHub secret `DEV_SSH_KNOWN_HOSTS`:

```bash
printf 'ssh-dev.yoursaynews.com '
cut -d' ' -f1-2 /etc/ssh/ssh_host_ed25519_key.pub
```

Verify the deployment account and Docker access:

```bash
id deploy
sudo -u deploy docker version
sudo -u deploy docker compose version
```

After the connector reports healthy in Cloudflare, test the configured human and CI Access paths.

## Firebase and Google Play placement

Firebase and Google Play values do not belong in `development.tfvars`.

| Value | Destination |
| --- | --- |
| Android package ID `com.yoursaynews.app` | Committed Expo/Android configuration |
| Firebase Android `google-services.json` | EAS file environment variable used during mobile builds |
| Firebase client project/app configuration | EAS development environment and mobile app configuration |
| Development API URL | EAS: `EXPO_PUBLIC_API_BASE_URL=https://dev.yoursaynews.com/api` |
| Play App Signing SHA-1/SHA-256 | Firebase Android application settings |
| Firebase Admin credential for backend verification | GitHub secret rendered as a protected VM runtime file |
| Hosted Firebase project/audience | Reviewed backend deployment configuration |
| Google Play submission service account | EAS/GitHub mobile-delivery secret only |

Current next steps:

1. Create or confirm the Play application with package ID `com.yoursaynews.app`.
2. Enable Play App Signing.
3. Register the development Firebase Android application with the same package ID.
4. Enable Google as a Firebase Authentication provider.
5. Add the EAS/upload and Play App Signing SHA-1/SHA-256 fingerprints to Firebase.
6. Obtain the updated `google-services.json`.
7. Link the shared Expo/EAS project and complete one manual signed development-store build.
8. Implement the hosted Firebase backend/mobile configuration and immutable Firebase UID account
   linking before remote authenticated testing.

The current remote application deployment still uses transitional OIDC inputs. Do not substitute
Firebase values into those fields without implementing the reviewed hosted Firebase migration.

## Intentional non-goals for now

- Do not implement or apply production infrastructure.
- Do not put Cloudflare Access browser redirects in front of the mobile API hostname.
- Do not admit real personal data until the later privacy, backup, alerting and operational gates
  are complete.
- Do not place Firebase, Google Play or SSH private-key material in Terraform state or committed
  files.
