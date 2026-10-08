# User Management

Administrators manage Kairos accounts from **Admin → Users**. The page lists each account's email address, role, identity provider, last login, and creation date.

Only users with the `ADMIN` role can open this page or change accounts. User management does not affect API keys; manage those separately under **Admin → API keys**.

## Create a local user

1. Open **Admin → Users** and choose **Add User**.
2. Enter the user's email address.
3. Enter a password of at least eight characters.
4. Choose `USER` for a regular account or `ADMIN` for an administrator.
5. Choose **Add User**.

Local users sign in with their email address and password on the Kairos login page. The password is stored as a one-way hash and is never shown again after creation.

Give an account the `ADMIN` role only when it needs to configure Kairos or manage other users. A regular `USER` can sign in and use authenticated status features but cannot access the administration area.

## Change a local password

On the row for a local (`LOCAL`) account, choose **Change password**. Enter the new password twice and choose **Save password**. Passwords must contain at least eight characters, and both entries must match.

The change takes effect immediately. It does not change the user's email address or role, and the existing login session is not used to display or recover the old password. Users can continue to sign in with the new password after their next login.

If an account is missing, or the password is too short, Kairos leaves the stored password unchanged and shows an error. Password changes are protected by the normal administrator authorization and CSRF protection.

## OIDC users

OIDC users authenticate through the configured OpenID Connect provider. Kairos can create these accounts automatically after a successful provider login when `OIDC_CREATEUSERS=true` (the default). Newly provisioned OIDC users receive the `USER` role.

OIDC users do not have a local Kairos password. Their row is marked `OIDC` and shows **Password managed by identity provider** instead of a password-change control. Change their password, reset access, or enforce provider policies in the identity provider's administration console.

Administrators can still change an OIDC user's Kairos role in **Admin → Users**. To restrict login to accounts that have already been approved in Kairos, set `OIDC_CREATEUSERS=false`; the provider identity must then match an existing Kairos user.

See [OIDC / OAuth2 configuration](configuration-oidc.md) for issuer and client settings, certificate requirements, and provisioning behavior.

## Default administrator

On first startup, Kairos creates the local administrator `admin@kairos.local` with the password `admin`. Change it immediately after the first login using the steps above. The initial password is intentionally short for compatibility with existing installations; replacement passwords must contain at least eight characters.

See [Security Concepts](configuration-security.md) for public access and role-related security settings.
