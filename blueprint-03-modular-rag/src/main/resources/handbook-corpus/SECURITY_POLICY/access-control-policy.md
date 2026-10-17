effective_date: 2026-01-10
# Access control policy

## Multi-factor authentication
MFA is mandatory for every production console, VPN and cloud account; hardware keys are required for administrators.

## Secrets
Secrets must be stored in the vault and rotated every 90 days; secrets in source code or CI logs are a reportable security incident.

## Access reviews
Managers review production access every quarter and revoke access that is no longer needed within 5 working days.
