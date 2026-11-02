# Public-release checklist

Preparation may proceed despite the documented technical blockers by owner approval. **Changing repository visibility is not authorized.** Publishing an application and publishing its source are separate decisions.

## Prepared and checked

- [x] Concise README with implemented, staging-verified, experimental and pending states.
- [x] Authentic screenshots with public test locations/synthetic data and attribution.
- [x] Architecture diagram grounded in the current implementation; optional workers/research boundaries explained.
- [x] Local setup, test commands, API references and scientific limitations.
- [x] Contribution guide, issue forms, PR template, conduct and security policies prepared.
- [x] Relevant repository description/topics and meaningful acceptance issues.
- [x] Existing functional/monitoring CI evidence recorded honestly; overall security failure remains visible.
- [x] Current source and advertised reachable history examined with a redacted secret scanner and additional path/credential/PII patterns; no confirmed credential found in that bounded review.

## Required before publication

- [ ] Obtain an accepted security disposition and complete any required remediation; current strict CI fails.
- [ ] Re-scan the exact final refs/artifacts immediately before publication. Review reports manually; automated scans cannot certify absence of secrets.
- [ ] Owner accepts existing Git author/committer metadata exposure or separately authorizes remediation. No history rewrite or production credential rotation is implied.
- [ ] Owner chooses the application software licence; no licence file has been added.
- [ ] Confirm copyright/contributor rights, logo/asset/trademark rights and intended contribution terms.
- [ ] Resolve third-party licence/notice/source-distribution obligations for exact packages and binaries, including native libraries and container OS components.
- [ ] Confirm research dataset attribution/redistribution and provider-specific downstream/commercial rights. Code licensing does not license provider access.
- [ ] Establish an owner-approved private security/conduct reporting channel and verify it works. Do not publish sensitive issue details.
- [ ] Review all intended docs, historical commits, screenshots, CI artifacts and deployment references for private information. Keep internal review reports, operational credentials and exports out of publication.
- [ ] Review free-demo limits, scientific uncertainty and latest provider/device/operations status with the owner; do not present staging as production acceptance.
- [ ] Update README visibility/status wording for the actual publication decision and approved revision.
- [ ] Obtain explicit owner authorization to change `Ap-95671/custerra` from private to public.

Production deployment, paid infrastructure, final licensing, personal-data disclosure, credential rotation and destructive/history changes remain separate authorizations. No optional bot, Discussion feature or paid scanning product was enabled by this preparation.
