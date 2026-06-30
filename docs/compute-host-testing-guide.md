# Compute Host REST Testing Guide

This guide covers how to use the Omega Compute Host plugin controller endpoints to manually
exercise the plugin-SDK context APIs `MorpheusComputeServerService.addHost` / `removeHost`
(MORPH-12852). These endpoints are the only way to invoke the new context API from outside the
platform — no native UI or bundled cloud calls it.

> **Note:** The cloud's **Hosts > Add Resource > Add Omega Bare Metal Stub Server** button does
> NOT exercise this harness. That button posts to `ServersController`, which calls
> `computeClusterService.addServer(opts)` directly. Our `addHost` is a sibling caller of the
> *same* delegate, reached only via `morpheusContext.services.computeServer.addHost(...)`. Use the
> UI button as a sanity check that the cloud can create a stub host; use this harness to test the
> MORPH-12852 code path.

## Prerequisites

1. Morpheus UI running locally with the omega test plugin loaded
2. The omega plugin JAR built against SDK `1.4.2-SNAPSHOT` and deployed (`./gradlew shadowJar`)
3. A backend build containing the `addHost`/`removeHost` impl (branch
   `jayg/morph-12852/addRemoveHostApi` or later)
4. An Omega Baremetal cloud created and assigned to a group (note the cloud ID and group ID)

## Authentication

The `/plugin/**` routes require **session-based authentication** with CSRF tokens.
Bearer token (API key) authentication does **not** work on plugin routes — it only applies
to `/api/**` endpoints.

### Establishing a Session

```bash
# Set your appliance credentials first (do not hard-code them in scripts):
export MORPH_USER='<your-username>'
read -rs MORPH_PASS   # prompts without echoing the password

# 1. Fetch the login page and extract the CSRF token
curl -s -c /tmp/morph_cookies.txt http://localhost:8080/login/auth > /tmp/morph_login.html
LOGIN_CSRF=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/morph_login.html | sed 's/.*value="//;s/"//')

# 2. Authenticate (POST to /login/process, NOT /login/authenticate)
curl -s -b /tmp/morph_cookies.txt -c /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/login/process \
  --data-urlencode "username=$MORPH_USER" \
  --data-urlencode "password=$MORPH_PASS" \
  --data-urlencode "_csrf=$LOGIN_CSRF" \
  -o /dev/null

# 3. Get a session CSRF token for subsequent POST requests
#    (extracted from meta tag on any authenticated page)
SESSION_CSRF=$(curl -s -b /tmp/morph_cookies.txt http://localhost:8080/operations/activity \
  | grep -o 'name="_csrf" content="[^"]*"' | sed 's/.*content="//;s/"//')

echo "Session CSRF: $SESSION_CSRF"
```

> Use your own appliance credentials for `MORPH_USER` / `MORPH_PASS`. On a local dev appliance the
> values can be sourced from `morpheus-ui/.grcc` (`USER` / `PASSWORD`) if you have it set up.

### Making Authenticated Requests

- **GET requests:** Only need the session cookie (`-b /tmp/morph_cookies.txt`)
- **POST requests:** Need both the session cookie AND the CSRF token header

```bash
# GET example (no CSRF needed) — any authenticated page works
curl -s -b /tmp/morph_cookies.txt http://localhost:8080/operations/activity -o /dev/null -w "%{http_code}\n"

# POST example (CSRF required via X-XSRF-TOKEN header)
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/add \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"cloudId": 12, "serverTypeCode": "omega.baremetal.stub-server"}'
```

> **Important notes:**
> - The CSRF token comes from the `<meta name="_csrf" content="...">` tag, NOT from the
>   `XSRF-TOKEN` cookie value (they are different).
> - Send the token as the `X-XSRF-TOKEN` header on all POST requests.
> - Sessions expire after inactivity; re-authenticate if you get 302 redirects to `/login/auth`.
> - The login endpoint is `/login/process` (not `/login/authenticate`).

### Verify the Plugin is Loaded

POST to `/add` with an empty body — a loaded controller responds with a `400` and
`cloudId is required`, which confirms the route is wired (rather than a `404`/login redirect):

```bash
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/add \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{}'
# {"success":false,"msg":"cloudId is required"}
```

---

## Permission Requirement

All controller routes require the `admin-appliance` permission with `full` access.
This is satisfied by the System Admin role.

---

## Finding IDs

These lookups query the dev database directly — no extra tooling required. Open a MySQL shell
against your dev schema (the dev appliance defaults to user `root`, no password):

```bash
mysql -u root morpheus_development   # adjust schema/connection for your appliance
```

```sql
-- Omega Baremetal clouds (use a cloud id for cloudId)
SELECT z.id, z.name FROM compute_zone z
  JOIN compute_zone_type zt ON z.zone_type_id = zt.id
  WHERE zt.code = 'omega.baremetal.cloud';

-- Groups (use a group id for siteId)
SELECT id, name FROM compute_site LIMIT 10;

-- Existing omega stub hosts (use a server id for remove)
SELECT s.id, s.name, s.status FROM compute_server s
  JOIN compute_server_type st ON s.compute_server_type_id = st.id
  WHERE st.code = 'omega.baremetal.stub-server';

-- Resource pools for a cloud (for config.resourcePoolId, if the cloud uses pools)
SELECT id, name FROM compute_zone_pool WHERE zone_id = <CLOUD_ID>;
```

> **If you have grcc:** the `grcc` Groovy console client runs these lookups as live domain
> queries instead of SQL. Run it from `morpheus-ui/` so it picks up `.grcc` (HOST / USER /
> PASSWORD) and targets the running instance. Use fully-qualified `com.morpheus.*` classes — the
> preamble also imports `com.morpheusdata.model.*`, so bare `Cloud`/`ComputeServer` are ambiguous.
> The four queries above map to:
> ```groovy
> grcc --inline-code '
> com.morpheus.ComputeZone.where { zoneType.code == "omega.baremetal.cloud" }.list().each { println "cloud ${it.id} ${it.name}" }
> com.morpheus.ComputeSite.list([max:10]).each { println "group ${it.id} ${it.name}" }
> com.morpheus.ComputeServer.where { computeServerType.code == "omega.baremetal.stub-server" }.list().each { println "server ${it.id} ${it.name} ${it.status}" }
> com.morpheus.ComputeZonePool.where { zone.id == <CLOUD_ID> }.list().each { println "pool ${it.id} ${it.name}" }
> '
> ```

---

## Test Scenarios

### 1. Validation and Not-Found Paths

**Goal:** Confirm requests route to the handler and the context API guard clauses respond
correctly — no real cloud needed.

```bash
# Missing cloudId
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/add \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{}'
# {"success":false,"msg":"cloudId is required"}

# Nonexistent cloud
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/add \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"cloudId":999999,"serverTypeCode":"omega.baremetal.stub-server"}'
# {"success":false,"msg":"Cloud not found for cloudId: 999999"}

# Missing serverId
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/remove \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{}'
# {"success":false,"msg":"serverId is required"}

# Nonexistent server
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/remove \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"serverId":999999}'
# {"success":false,"msg":"ComputeServer not found for serverId: 999999"}
```

> **Tip:** Pass a real `cloudId` with a wrong `serverTypeCode` to discover valid host types —
> the response echoes `availableTypes` for that cloud.

---

### 2. Add a Host

**Goal:** Add a stub host to the Omega Baremetal cloud via the context API.

```bash
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/add \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{
    "cloudId": <CLOUD_ID>,
    "serverTypeCode": "omega.baremetal.stub-server",
    "serverName": "omega-host-1",
    "siteId": <GROUP_ID>,
    "licenseCheck": false,
    "config": { "resourcePoolId": "<POOL_ID_IF_REQUIRED>" }
  }'
```

**Expected response:**
```json
{"success": true, "msg": null, "errors": {}, "data": {"id": 1234, "name": "omega-host-1"}}
```

**Verify:** The new host appears under the cloud's **Hosts** tab, or via the `compute_server`
lookup above. Capture `data.id` for the remove scenario.

> **Note:** Provide either `serverTypeCode` or `serverTypeId`. `licenseCheck: false` skips
> license/connectivity checks (recommended for the stub cloud). If the cloud scopes hosts to a
> resource pool, set `config.resourcePoolId` (or `poolId`); otherwise `addServer` returns
> `success: false` with a pool error.

---

### 3. Remove a Host

**Goal:** Remove the host created in scenario 2 via the context API.

```bash
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/compute-hosts/remove \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"serverId": <SERVER_ID>, "force": true, "removeResources": true}'
```

**Expected response:**
```json
{"success": true, "msg": null, "errors": null, "data": null}
```

**Verify:** The host disappears from the cloud's **Hosts** tab (or the `compute_server` lookup
returns it with a removed/decommissioned status).

> **Note:** `force: true` bypasses other checks and `removeResources: true` removes associated
> resources. Set `removeInstances: true` to delete managed instances on the host, and
> `skipPolicyCheck: true` to bypass policy validation. `userId` is optional (defaults to the
> process `system` user).

---

## Endpoint Reference

### POST /plugin/compute-hosts/add

Builds an `AddHostRequest` and calls `services.computeServer.addHost(cloud, request)`.

**Body:**
```json
{
  "cloudId": 12,
  "serverTypeCode": "omega.baremetal.stub-server",
  "serverName": "omega-host-1",
  "siteId": 2,
  "licenseCheck": false,
  "config": { "resourcePoolId": "1" }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `cloudId` | Yes | Target cloud (zone) ID |
| `serverTypeCode` | One of code/id | `ComputeServerType.code` of the host type to add |
| `serverTypeId` | One of code/id | `ComputeServerType.id` (alternative to code) |
| `serverName` | No | Display name; auto-generated if omitted |
| `hostname` | No | Hostname; defaults to `serverName` |
| `siteId` | No | Group/site ID to provision into |
| `planId` | No | `ServicePlan` ID (sizing) |
| `poolId` | No | `CloudPool` ID → sets `config.resourcePoolId` |
| `licenseCheck` | No (default `true`) | `false` skips license/connectivity checks |
| `config` | No | Passthrough map; may include `resourcePoolId` and a `server` sub-map |

**Response:**
```json
{"success": true, "msg": null, "errors": {}, "data": {"id": 1234, "name": "omega-host-1"}}
```

### POST /plugin/compute-hosts/remove

Resolves the `ComputeServer`, builds a `RemoveHostRequest`, and calls
`services.computeServer.removeHost(server, request)`.

**Body:**
```json
{"serverId": 1234, "force": false, "removeResources": false, "removeInstances": false, "skipPolicyCheck": false, "userId": 1}
```

| Field | Required | Description |
|-------|----------|-------------|
| `serverId` | Yes | `ComputeServer` ID to remove (host identity; not in the DTO) |
| `force` | No (default `false`) | Force the removal, bypassing other checks |
| `removeResources` | No (default `false`) | Also remove associated resources |
| `removeInstances` | No (default `false`) | Also remove associated instances |
| `skipPolicyCheck` | No (default `false`) | Skip policy validation |
| `userId` | No | ID of the requesting user (defaults to process `system` user) |

**Response:**
```json
{"success": true, "msg": null, "errors": null, "data": null}
```

---

## Execution Pipeline

Understanding the execution flow helps debug issues:

**addHost**
1. **Controller** resolves the `Cloud` (`services.cloud.get`), the `ComputeServerType`
   (`async.cloud.getComputeServerTypes(cloudId)`, matched by id/code), and optional plan/pool,
   then builds an `AddHostRequest`.
2. **`services.computeServer.addHost(cloud, request)`** → `MorpheusSynchronousComputeServerImplService.addHost`
   (`.blockingGet()` on the async impl).
3. **`MorpheusComputeServerImplService.addHost`** loads the `ComputeZone`, calls `buildOpts(request)`
   (maps serverType/name/plan into `opts`, pool → `config.resourcePoolId`), sets `opts.zoneId`/`opts.account`.
4. Calls **`computeClusterService.addServer(opts)`** — the same delegate the native UI uses.
5. Marshals `result.server` to a `ComputeServer` model and returns `ServiceResponse.success(server)`.

**removeHost**
1. **Controller** resolves the `ComputeServer` (`services.computeServer.get`) and builds a `RemoveHostRequest`.
2. **`services.computeServer.removeHost(server, request)`** → `MorpheusComputeServerImplService.removeHost`.
3. Loads the domain `ComputeServer`, builds `opts`
   (`force`/`removeResources`/`removeInstances`/`skipPolicyCheck`/`userId`), and calls
   **`computeService.deleteServer(opts)`**.
4. Returns `ServiceResponse.success()` or `ServiceResponse.error(...)`.

---

## Troubleshooting

### 302 Redirect on POST requests

Your session has expired. Re-authenticate using the login flow above.

### 403 Forbidden on POST requests

Missing or invalid CSRF token. Make sure you're sending the `X-XSRF-TOKEN` header
with the value from `<meta name="_csrf" content="...">` (not the cookie value).

### `add` returns "ComputeServerType not found"

The `serverTypeCode`/`serverTypeId` does not match a host type the cloud exposes. The response
echoes `availableTypes` for that cloud — copy a valid `code` from there. For the omega cloud it is
`omega.baremetal.stub-server`.

### `add` returns `success: false` from the delegate

`computeClusterService.addServer` rejected the opts. Common causes:
- The cloud requires a resource pool — set `config.resourcePoolId` (or `poolId`); find one via the
  `compute_zone_pool` lookup.
- A required plan or site is missing — set `planId`/`siteId`.
- Try the native **Hosts > Add Resource** button once to see which fields the cloud requires.

### `msg` is null but `errors` is populated

The SDK `ServiceResponse.error(String)` single-arg shim stores the message under `errors['error']`
and leaves `.msg` null. Check `errors` when `msg` is null. (The MORPH-12852 guard clauses use
`error(msg, [:])`, which does set `.msg`.)

### 404 on /plugin/compute-hosts/add or /remove

- Confirm the freshly built `*-all.jar` is in the stack's plugin directory
  (`~/git/morpheus-plugin-core/plugins`) and the old jar was removed.
- Confirm the backend is on a build that contains the `addHost`/`removeHost` impl.
- Confirm `OmegaComputeHostController` is registered in `MorpheusOmegaTestPlugin.initialize()`.

### Plugin load error: UnsupportedOperationException in DynamicTemplateLoader

The plugin sets a `NoOpRenderer` to work around `PluginManager.registerPlugin()` calling `.add()`
on a fixed-size list when a plugin has controllers but no custom renderer — do not remove it.
