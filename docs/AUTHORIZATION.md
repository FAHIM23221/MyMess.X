# Authorization plan (Phase 1)

The app UI is never a security boundary. Every rule below is enforced by Supabase
(Row Level Security, role checks inside SQL functions, triggers). The app only mirrors it.

| Action | MEMBER | ADMIN | MAIN_ADMIN |
|---|---|---|---|
| Read own profile | yes | yes | yes |
| Read all profiles | no | yes | yes |
| Edit own name | yes | yes | yes |
| Change own role/status | no | no | no |
| Create member invite code | no | yes | yes |
| Create admin invite code | no | no | yes |
| Suspend/activate a MEMBER (reason required) | no | yes | yes |
| Suspend/activate an ADMIN | no | no | yes |
| Change roles (reason required) | no | no | yes |
| Toggle leaderboard | no | no | yes |
| Read audit log | no | no | yes |

Invariants (database level):
- Registration needs a valid, unused, unexpired invite code. Role comes only from the invite, never from the client.
- At most one MAIN_ADMIN; it cannot be demoted, suspended or deleted through normal operations.
- Limits: 20 members, 5 admins (settings, changeable only by MAIN_ADMIN).
- Role, status and audit entries cannot be written directly by the app; only through audited functions.
- Login is phone + password. The phone is mapped to the technical address `<phone>@mess.app` (not a real email).
- The public Supabase key is in the APK by design. The `service_role` key must never be in the app or repository.
