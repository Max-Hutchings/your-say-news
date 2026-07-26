# Private R2 bucket module

Owns the EU-jurisdiction private media bucket, lifecycle/CORS policy and deletion protection.

The module must not place access-key secrets in normal outputs. Bucket deletion remains a
break-glass operation and must be blocked while objects exist.
