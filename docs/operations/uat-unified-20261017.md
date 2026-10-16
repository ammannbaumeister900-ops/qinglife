# Unified UAT candidate: 1.4.22-uat.1017

This is a local acceptance candidate, based on `2e96fe6`, on branch
`uat/20261017-unified`. No deployment or WeChat upload is performed by building it.
The release manifest outside Git records the final commit, artifact hashes and
actual test results; this document alone does not certify acceptance.

## Included scope

- Existing homepage V2 and about/conversion page, with session read-state contract.
- Previously deployed account/role permissions, immediate loss of access, and
  staff binding controls. Preserve existing permissions and identities.
- Private article drafts, authenticated images, preserved author/time, and no
  unread fan-out for disabled drafts. Public article queries exclude drafts.
- Public rich-text/article reading, explicit source sharing, native video,
  optional covers, retries and lifecycle cleanup.
- Current-session public voice pagination/full text, publication evidence and
  consent checks, revocation filtering, and stale-response isolation.
- Staff search/selection and session-number presentation; existing quotation
  and cancellation/reapplication rules remain intact.

M2 participant finance, bulk legacy import, real story publication, and story
linking from the conversion page are excluded. Unapproved editorial slots stay
empty. Existing ancestor finance code is retained without new M2 behavior.

## Build and database contract

Use Java 8 and the committed admin pnpm lockfile. Do not upgrade dependencies.
Validate miniapp tests, backend unit/package checks, changed-admin-file strict
lint, and dedicated loopback MySQL/Redis integration tests. Test identities and
content are synthetic. Browser/DevTools checks do not replace real-device or
business acceptance.

The candidate contains the 38 existing migration scripts. The previous UAT
inventory matched their normalized hashes, including V1_4_20 and V1_4_21.
Recheck the migration ledger before any future deployment; do not edit or rerun
applied ALTER scripts. V1_4_21 supplies article bylines; retaining this migration
does not enable the excluded importer. Never deploy account or byline code to
an incompatible database.

For the final admin artifact, explicitly set:

```
VUE_APP_PUBLIC_PATH=/uat-20261011-admin/
VUE_APP_BASE_API=/uat-20261011-api
```

Generate the private-draft preview shell with
`node scripts/build-article-draft-preview.cjs <output.html>`. Only this empty,
authenticated shell may be web-readable; draft content and images remain
authenticated. Keep it alongside the admin build in any later authorized UAT
deployment. The ordinary editor does not attach authentication to image tags.

Build the miniapp using `scripts/package-uat-miniapp.cjs 1.4.22-uat.1017`.
It sets the UAT API and legal-domain checks in a separate ignored artifact;
do not change the source runtime or point local integration tests at remote UAT.
Test-admin builds use local test routes. Final UAT artifacts are not used by the
local integration browser, preventing it from issuing requests to remote UAT.

## Release boundary

Future deployment requires separate approval, backups, backend-before-client
contract rollout, admin/preview verification, experience-version assignment and
a new matching QR code. Keep media, database dumps, credentials, raw business
documents and private test artifacts outside Git. iOS/Android sharing/video,
authorized content and final business confirmation remain acceptance tasks.
