# API Keys and Permissions

API keys let automation and MCP clients access Kairos without using a person's session. Each key has an explicit set of permissions, so a client only receives the access it needs.

## Create a key

1. Sign in as an administrator and open **Admin → API Keys**.
2. Enter a descriptive name for the client.
3. Select at least one permission. New keys start with **Read status** selected.
4. Select **Create API key** and copy the token immediately. Kairos shows it only once.

Use the token with either supported authorization scheme:

```http
Authorization: Bearer <api-key-jwt>
```

```http
Authorization: ApiKey <api-key-jwt>
```

## Permission reference

| Permission | REST access | MCP tools |
|---|---|---|
| `STATUS_READ` | Protected resource, status, history, outage, and announcement reads | `listResources`, `getResource`, `getCheckHistory`, `listAnnouncements`, `listOutages`, `getCheckAuditLog` |
| `CHECK_EXECUTE` | Reserved for endpoints that execute checks | `triggerCheck`, `runInstantCheck` |
| `RESOURCE_MANAGE` | Create template or individual resources; delete resources | `createResource`, `deleteResource` |
| `ANNOUNCEMENT_MANAGE` | Create, update, and delete announcements | `createAnnouncement`, `deleteAnnouncement` |
| `MCP_ACCESS` | Connect to `/sse` and `/mcp/**` | Allows MCP transport access; every tool also requires the matching permission above |

Kairos has no API for manually creating outages. A configured or instant check can cause or resolve an outage as part of check processing; permission to run those checks is controlled by `CHECK_EXECUTE`.

### Public reads

When public access is enabled, public REST status endpoints remain readable without a key, so they also work with a key that does not have `STATUS_READ`. When public access is disabled, an API key needs `STATUS_READ` for those endpoints. Resource group visibility rules still apply.

## Least-privilege examples

| Client | Permissions |
|---|---|
| Status dashboard or monitoring export | `STATUS_READ` |
| Automation that triggers checks and reads results | `STATUS_READ`, `CHECK_EXECUTE` |
| Deployment automation that maintains resources | `STATUS_READ`, `RESOURCE_MANAGE` |
| Announcement automation | `STATUS_READ`, `ANNOUNCEMENT_MANAGE` |
| Read-only MCP assistant | `MCP_ACCESS`, `STATUS_READ` |
| MCP assistant that runs checks and reads results | `MCP_ACCESS`, `STATUS_READ`, `CHECK_EXECUTE` |
| Full administrator automation | All five permissions |

Use **Select full access** only for a client that needs every capability.

## Change, rotate, or revoke a key

Select **Edit permissions** beside a key, choose at least one permission, and save. The change applies to the existing token immediately; clients do not need a new token.

Kairos cannot redisplay a token. To rotate one, create a replacement key, update the client, verify it works, and delete the old key. Deleting a key revokes its token immediately.

Treat tokens like passwords. Store them in a secret manager, do not commit them to source control, and rotate or revoke a token if it may have been exposed.

## Upgrading

The database migration grants all five permissions to keys that existed before scoped permissions were introduced. Their tokens continue to work without rotation. Review these keys after upgrading and reduce each one to the permissions its client needs.

## Troubleshooting

- A REST response with `403 Forbidden` means the token is valid but lacks the permission for that operation. Edit the key in **Admin → API Keys**.
- A `401 Unauthorized` response means the token is invalid, malformed, or belongs to a deleted key. Check the authorization header or rotate the key.
- An MCP client needs `MCP_ACCESS` to connect and a second operational permission for each tool it calls. Tool discovery still lists the complete Kairos tool catalog; a disallowed call returns an error naming the required permission.
- Permission edits take effect on the next request. Reconnect an MCP client if it caches a failed connection or tool result.

See the [REST API reference](api.md) and [MCP server guide](mcp-server.md) for endpoint and client setup details.
