# Linux Compose host module

Owns the single Hetzner VM, firewall, SSH-key references, rebuild/delete protection and the minimal
host bootstrap needed for Docker Compose and private Tailscale operations.

It must not deploy application images, render runtime secrets or open public inbound API/SSH ports.
Those remain deployment-workflow responsibilities.
