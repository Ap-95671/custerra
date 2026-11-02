# Security policy

Custerra is an experimental application under release review. No production-supported release or independent security certification is currently declared. Dependency and container security findings remain open; a successful build does not mean security acceptance.

## Report a vulnerability privately

If this repository offers **Security → Report a vulnerability**, use that private reporting flow. Its availability must be verified in the repository UI; use the private flow rather than an ordinary issue. No external reporting email is designated.

Until that channel is available, do not submit exploit details, credentials or personal data in public issues, pull requests, screenshots or discussions. Ordinary issue templates are for non-sensitive defects only. Do not guess a maintainer email address or send private findings to an unverified recipient.

A private report should identify the affected revision, impact, minimal reproduction and any safe mitigation. Use synthetic accounts and public test coordinates. Do not include live credentials, database exports or unrelated user information.

## Testing boundaries

- Test only environments and accounts you are explicitly authorized to use.
- Do not scan shared providers, exhaust quotas, access another account’s records or send fabricated hazard warnings.
- Keep provider keys, database passwords, operations tokens and push private keys in environment-specific secret storage.
- Do not disable verified TLS, account ownership, CSRF or strict security checks to make a test pass.
- Real push delivery, independent review and production rollout require their own acceptance evidence and authorization.

## Publication status

The owner approved public source release under Apache-2.0 on 2 November 2026 after a bounded publication review. Current source, advertised reachable history and retained CI logs/artifacts were reviewed separately from outstanding runtime vulnerability acceptance. Public source is not production release acceptance. No guaranteed response time or bug-bounty payment is offered.
