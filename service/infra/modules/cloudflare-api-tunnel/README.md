# Cloudflare API tunnel module

Owns the Cloudflare Tunnel and DNS record that route the development API hostname to
`post-service:8082` over the private Compose network.

The module must expose no tunnel credential as a non-sensitive output and must protect tunnel/DNS
resources from accidental replacement or deletion.
