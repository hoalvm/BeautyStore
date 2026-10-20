# BeautyStore frontend audit — 2026-10-19

## Scope and Git boundary

Branch `frontend` starts at `2b78acde570b4a3960e3c77213f6b83498a2f62d`
(the current `dev`/`backend` state when work began). The repository directory is
`BeautyStore`, not the old `Toy-Store-Web` path. All 20 new commits use the existing
configured identity `PHAMQUOCDUY11052006`. No remote push, merge, history rewrite,
backend API change or migration modification is part of this audit.

Reviewed storefront, auth/profile, cart/checkout/payment/order, admin, shipper,
chat/support scripts, their Thymeleaf templates, shared CSS, policy/email template
references and frontend/backend request contracts. This is a focused correctness
and usability audit, not a claim that every possible defect has been eliminated.

## Changes in the 20 commits

1. Shared helpers: correct zero sale prices, safe web image URLs, gallery fallback,
   availability and store-local time parsing; dependency-free test/check runner.
2. Product interactions: variant-specific gallery, valid integer quantities,
   duplicate request guard and asynchronous favorite state.
3. Catalog: discard stale filter responses, browser history synchronization,
   ingredient/hair concern/finish filters, valid URL pagination.
4. Cart: serialize mutations, refresh authoritative quantities/totals, discard
   stale voucher calculations.
5. Checkout: keyboard form submission, DTO-aligned limits, single-flight submit,
   voucher revalidation and shipping/discount breakdown.
6. Payment waiting: Vietnam timezone deadlines, terminal reconciliation handling,
   stop expired timers and prevent polling resurrection.
7. Storefront cards: full price ranges, archive/stock availability, explicit
   variant selection and recoverable favorite actions.
8. Authentication: one handler per action, guest cart badge, stale JWT cleanup,
   OTP cooldown/state and same-origin login return paths.
9. Profile: correct roles and optional fields, safe text rendering, password
   policy and reauthentication after JWT revocation.
10. Credential forms: password-manager autofill, input limits and live errors.
11. Recovery forms: editable email on direct navigation, numeric OTP autofill,
    reset resend and account-discovery-safe messages.
12. Chat: no initial focus theft, keyboard suggestions, hidden polling/read
    suppression, focus restoration and short-mobile viewport sizing.
13. Orders: honest canceled/failed/reconciliation states, scoped guest access,
    resend/expiry recovery, cached receipt notice and keyboard review actions;
    cart controls capped at 999 with accessible labels.
14. Admin catalog/vouchers: preserve taxonomy and variant flags, partial-create
    recovery, valid page limits, retain archived voucher scopes.
15. Admin orders/dashboard: valid status transitions, captured-payment guard,
    stale-response suppression and refreshed alerts.
16. Admin accounts: current password policy, active/inactive filter, error handling
    and duplicate submit protection.
17. Admin support: isolate in-flight conversations and drafts by session.
18. Shipper: remove implicit global event and timer races, serialize delivery
    updates and label lifetime statistics accurately.
19. Moderation: restore refresh/logout, bounded pagination, prevent duplicate
    moderation/refund/restock operations; label page-local filters honestly.
20. Verification: native header search, keyboard skip target, correct selected
    gallery styling/self-hosted heading font, CI and real Thymeleaf rendering tests.

## Reproduce automated checks

Final local results: 87/87 Node tests passed. Maven `clean verify` completed with
202 Java tests: 201 passed, 0 failures, 0 errors, 1 MySQL Testcontainers test skipped
because Docker is unavailable. The 31 Thymeleaf rendering cases are included in
that total. The syntax/asset checker passed for 26 JavaScript sources (including
inline template code). A separate `-DskipTests compile` also passed.

```powershell
npm run check
npm test
.\mvnw.cmd clean verify
.\mvnw.cmd -DskipTests compile
git diff --check
git rev-list --count 2b78acd..frontend
```

Node tests use built-in `node:test`/`vm`, without npm dependencies. They execute
frontend behavior with controlled DOM/fetch/timer doubles; they do not replace
browser coverage. The syntax checker also checks inline JavaScript and local
template asset references. `FrontendRenderingTests` renders 31 real route cases,
including role-protected pages, product canonical metadata and the legacy redirect.

GitHub Actions runs Node checks and Thymeleaf rendering on `frontend` pushes and
pull requests. The existing backend verification workflow remains unchanged.

## Browser evidence

Browser checks used a separate local test-profile app on port 18080, explicitly
loading `file:src/test/resources/application.properties` with an in-memory H2
database. Only synthetic/demo users and orders were used. Database test state is
discarded when the process stops; no live payment or email was required.

- Guest product → cart quantity 2 → checkout via Enter → COD receipt → OTP access
  form completed; cart became empty. Fractional quantity was rejected.
- Login returned to `/favorites`; external redirect was rejected. Profile update
  persisted; valid password change cleared the JWT and required a new login.
- Direct reset/verify forms accepted email input, preserved it through refresh,
  and enforced resend cooldown. No real email delivery was tested.
- Admin catalog, variant/gallery editor, accounts, moderation/support empty states
  and voucher scope initialization rendered without observed JavaScript errors.
- Shipper APIs returned 200 and tab/filter handlers worked. The automation tool
  did not reliably dispatch clicks on shipper links; physical mouse interaction
  on that screen still needs manual confirmation.
- Mobile catalog at 390px and chat at 320×568 had no horizontal overflow. Catalog
  filtering/back navigation, keyboard skip link, mobile header search and chat
  Escape/focus behavior were exercised.

## Remaining limits / follow-up

- VNPay sandbox/IPN end-to-end, real SMTP delivery, Cloudinary uploads and live AI
  responses require separately configured integrations and were not exercised here.
- MySQL Testcontainers needs Docker; a skipped local migration test is not evidence
  that MySQL migration verification passed. CI with Docker remains necessary.
- Some admin review and search+stock combinations filter within the current page
  because the API does not provide global combined filtering. The UI now says so
  and preserves access to later pages. Extending those API queries is backend work.
- Failed frontend multi-step product creation now preserves the created ID instead
  of making duplicates. Batch/image steps are still separate server transactions;
  the UI directs the administrator to inspect and complete the failed step.
- Bootstrap and icon assets still use the existing CDNs. Offline/CDN failure and
  exhaustive browser/device or assistive-technology coverage are not established.
- An initial runtime start found stale malformed `.class` build output; a clean
  Maven build removed it. Restart the app after replacing cached templates, or set
  `spring.thymeleaf.cache=false` only for local development.
