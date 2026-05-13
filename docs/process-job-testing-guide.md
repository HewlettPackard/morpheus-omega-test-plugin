# Process Job REST Testing Guide

This guide covers how to use the Omega Process Job plugin controller endpoints to set up
and manually test process-job features: auto-retry, retry with inputs, and cancelable steps.

## Prerequisites

1. Morpheus UI running locally with the omega test plugin loaded
2. A pre-existing instance/workload (note the workload ID)
3. The omega plugin JAR built and deployed (`./gradlew shadowJar`)

## Authentication

The `/plugin/**` routes require **session-based authentication** with CSRF tokens.
Bearer token (API key) authentication does **not** work on plugin routes — it only applies
to `/api/**` endpoints.

### Establishing a Session

```bash
# 1. Fetch the login page and extract the CSRF token
curl -s -c /tmp/morph_cookies.txt http://localhost:8080/login/auth > /tmp/morph_login.html
LOGIN_CSRF=$(grep -o 'name="_csrf" value="[^"]*"' /tmp/morph_login.html | sed 's/.*value="//;s/"//')

# 2. Authenticate (POST to /login/process, NOT /login/authenticate)
curl -s -b /tmp/morph_cookies.txt -c /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/login/process \
  --data-urlencode "username=morpheus" \
  --data-urlencode "password=P@ssw0rd" \
  --data-urlencode "_csrf=$LOGIN_CSRF" \
  -o /dev/null

# 3. Get a session CSRF token for subsequent POST requests
#    (extracted from meta tag on any authenticated page)
SESSION_CSRF=$(curl -s -b /tmp/morph_cookies.txt http://localhost:8080/operations/activity \
  | grep -o 'name="_csrf" content="[^"]*"' | sed 's/.*content="//;s/"//')

echo "Session CSRF: $SESSION_CSRF"
```

### Making Authenticated Requests

- **GET requests:** Only need the session cookie (`-b /tmp/morph_cookies.txt`)
- **POST requests:** Need both the session cookie AND the CSRF token header

```bash
# GET example (no CSRF needed)
curl -s -b /tmp/morph_cookies.txt http://localhost:8080/plugin/process-jobs/health

# POST example (CSRF required via X-XSRF-TOKEN header)
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/start-process \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"workloadId": 23}'
```

> **Important notes:**
> - The CSRF token comes from the `<meta name="_csrf" content="...">` tag, NOT from the
>   `XSRF-TOKEN` cookie value (they are different).
> - Send the token as the `X-XSRF-TOKEN` header on all POST requests.
> - Sessions expire after inactivity; re-authenticate if you get 302 redirects to `/login/auth`.
> - The login endpoint is `/login/process` (not `/login/authenticate`).

### Verify the Plugin is Loaded

```bash
curl -s -b /tmp/morph_cookies.txt http://localhost:8080/plugin/process-jobs/health
# {"status":"ok","provider":"omega.process-job"}
```

---

## Provider Configuration

The `OmegaProcessJobProvider` declares:
- `isRetryable() = true` — steps can be retried after failure
- `getRetryCount() = 3` — auto-retry up to 3 times before terminal `failed`
- `getRetryDelaySeconds() = 10` — wait 10s between auto-retries
- `isCancelable() = true` — steps show a cancel button in the UI while running

### Permission Requirement

All controller routes require the `admin-appliance` permission with `full` access.
This is satisfied by the System Admin role.

---

## Step Configuration Options

Each step accepts a `jobConfig` map that controls behavior:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `simulateFailure` | String ("true"/"false") | "false" | When "true", the step fails |
| `succeedOnAttempt` | Integer | 0 | If failing, auto-succeed after this many retry attempts (0 = never) |
| `sleepSeconds` | Integer | 5 | Seconds to sleep simulating work |
| `outputMessage` | String | "Omega step completed successfully" | Message passed to next step via `nextOpts` |

---

## Test Scenarios

### 1. Auto-Retry Policy

**Goal:** Verify the platform automatically retries failed steps per the provider's retry policy.

```bash
# Create a process with a step that fails initially but succeeds on retry attempt 2
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/start-process \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{
    "workloadId": 23,
    "stepConfigs": [{"simulateFailure": "true", "succeedOnAttempt": 2, "sleepSeconds": 2}]
  }'

# Note the processId and eventId from the response

# Dispatch the step
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/run \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"processId": <PROCESS_ID>, "eventId": <EVENT_ID>}'
```

**Expected behavior:**
- Step fails on attempt 0 → platform sets status to `waiting`, schedules retry after 10s
- Step fails on attempt 1 → platform schedules retry after 10s
- Step succeeds on attempt 2 → status becomes `complete`
- Total time: ~26s (2s execution × 3 attempts + 10s delay × 2 retries)

**Verify:** Check status via endpoint or Operations > History in the UI.

```bash
curl -s -b /tmp/morph_cookies.txt \
  "http://localhost:8080/plugin/process-jobs/status?processId=<PROCESS_ID>"
```

---

### 2. Retry with Inputs (Manual Retry)

**Goal:** Set up a step that exhausts auto-retries and lands on terminal `failed` status,
then retry it with corrected inputs via the UI retry flow.

```bash
# Create a step that always fails (succeedOnAttempt=99 means effectively never within 3 retries)
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/start-process \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{
    "workloadId": 23,
    "stepConfigs": [{"simulateFailure": "true", "succeedOnAttempt": 99, "sleepSeconds": 2}]
  }'

# Dispatch the step
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/run \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"processId": <PROCESS_ID>, "eventId": <EVENT_ID>}'
```

**Wait ~40 seconds** for all 3 auto-retries to exhaust (3 × ~12s each).

**Expected final state:** Event status = `failed`

**Manual testing in UI:**
1. Navigate to the process in Operations > History
2. The failed step should show a **"Retry"** button
3. Clicking it should present a form with the provider's option types:
   - Simulate Failure (checkbox)
   - Succeed On Attempt (number)
   - Sleep Seconds (number)
   - Output Message (text)
4. Uncheck "Simulate Failure" and submit — step should succeed on retry

> **Note:** The REST `/retry` endpoint stores input overrides in-memory on the provider
> instance, then dispatches the step. Due to the async RabbitMQ execution pipeline, the
> in-memory approach may not reliably apply overrides. The UI retry flow uses `stepInputs`
> which merges overrides directly into the event's stored `job_msg` config before dispatch,
> making it the reliable path for retry-with-inputs testing.

---

### 3. Cancelable Step

**Goal:** Set up a long-running step that is cancelable, so the UI shows a "Cancel" button.

```bash
# Create a step with a long sleep (300s gives plenty of time to test the cancel button)
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/start-process \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{
    "workloadId": 23,
    "stepConfigs": [{"sleepSeconds": 300}]
  }'

# Dispatch the step
curl -s -b /tmp/morph_cookies.txt -X POST \
  http://localhost:8080/plugin/process-jobs/run \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $SESSION_CSRF" \
  -d '{"processId": <PROCESS_ID>, "eventId": <EVENT_ID>}'
```

**Expected state:** Event transitions to `running` with `cancelable = true`

**Manual testing in UI:**
1. Navigate to the process in Operations > History
2. The running step should show a **"Cancel"** button
3. Clicking Cancel should set the event status to `cancelled`

> **Note:** The cancel mechanism is flag-based. The platform sets the DB status to `cancelled`,
> but the thread executing `Thread.sleep()` won't be interrupted. The step will appear cancelled
> immediately in the UI even though the background thread finishes its sleep naturally.

---

## Endpoint Reference

### GET /plugin/process-jobs/health

Returns server status. Useful for verifying the plugin is loaded and routing works.

**Response:**
```json
{"status": "ok", "provider": "omega.process-job"}
```

### POST /plugin/process-jobs/start-process

Creates a new process on an existing workload and inserts steps.

**Body:**
```json
{
  "workloadId": 23,
  "stepConfigs": [
    {"simulateFailure": "true", "sleepSeconds": 2},
    {"sleepSeconds": 5, "outputMessage": "Step 2 done"}
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `workloadId` | Yes | ID of the workload (container) to attach the process to |
| `stepConfigs` | No | Array of config maps, one per step to insert. Defaults to 1 step with empty config |

**Response:**
```json
{
  "success": true,
  "processId": 341,
  "steps": [
    {"eventId": 686, "stepTitle": "Omega Step 1"}
  ],
  "msg": "Process started for workload 23 with 1 step(s)"
}
```

### POST /plugin/process-jobs/run

Dispatches an existing process step for execution via the RabbitMQ pipeline.
The event status must be `pending` or `failed` — running/complete events cannot be re-dispatched.

**Body:**
```json
{"processId": 341, "eventId": 686}
```

### POST /plugin/process-jobs/retry

Re-dispatches a failed step with optional input overrides. Stores overrides in the provider's
in-memory map, then dispatches the step for execution.

**Body:**
```json
{"processId": 341, "eventId": 686, "inputs": {"simulateFailure": "false"}}
```

> See the note in Scenario 2 about reliability of in-memory overrides vs the UI retry flow.

### GET /plugin/process-jobs/status?processId=341

Returns process details and event status list.

**Response:**
```json
{
  "success": true,
  "processId": 341,
  "status": "complete",
  "events": [
    {"eventId": 686, "status": "complete", "message": "Omega step completed successfully"}
  ]
}
```

---

## Execution Pipeline

Understanding the execution flow helps debug issues:

1. **Controller** calls `morpheusContext.services.process.runProcessStep(request)`
2. **MorpheusProcessImplService** loads the `Process` and `ProcessEvent` from DB, then calls `processService.runStep()`
3. **ProcessService.runStep()** sets event status to `queued`, then sends a RabbitMQ message to `applianceJobQueue`
4. **ApplianceJobService** picks up the message, looks up the `ProcessJobType` by `jobName`, resolves the service bean
5. **PluginProcessJobService.executeProcessJob()** resolves the `ProcessJobProvider` via `pluginManagerService.findPluginProviderByCode()`, builds a `ProcessJobExecutionRequest` from the event's stored `job_msg`, and calls `provider.execute()`
6. On success → `onStepSuccess()` marks event `complete`
7. On failure → `onStepFailure()` either schedules a retry (status=`waiting`, sets `nextFire`) or marks terminal `failed`
8. **Scheduled retries** are picked up by `ProcessService.runScheduledEvents()` which fires events whose `nextFire` has passed

---

## Troubleshooting

### 302 Redirect on POST requests

Your session has expired. Re-authenticate using the login flow above.

### 403 Forbidden on POST requests

Missing or invalid CSRF token. Make sure you're sending the `X-XSRF-TOKEN` header
with the value from `<meta name="_csrf" content="...">` (not the cookie value).

### Step stuck in `running`

The process job execution thread may be blocked in `Thread.sleep()`. Solutions:
- Wait for the sleep to complete
- Force-complete the blocking event in the DB:
  ```sql
  UPDATE process_event SET status='cancelled', end_date=NOW() WHERE id=<event_id>;
  ```
- Restart the Morpheus application (clears stuck threads)

### Step stuck in `queued`

The RabbitMQ consumer processes jobs sequentially per job type. If a previous step is still
`running`, new steps queue behind it. Wait or cancel the running step.

### `/run` returns `success: false`

- Verify the processId and eventId exist in the database
- Check that the event's `job_name` matches a registered `ProcessJobType.code`
- Ensure the `ProcessJobType` seed record exists:
  ```sql
  SELECT * FROM process_job_type WHERE code='omega.process-job';
  ```

### Plugin load error: UnsupportedOperationException in DynamicTemplateLoader

This occurs because `PluginManager.registerPlugin()` calls `.add()` on a fixed-size list
created by `Arrays.asList()` when a plugin has controllers but no custom renderer.
The workaround is the `NoOpRenderer` class set on the plugin — do not remove it.

### Cancelable flag not set on new events

The `cancelable` flag is propagated from `ProcessJobType` at insert time. If it's not set:
```sql
UPDATE process_job_type SET cancelable=1 WHERE code='omega.process-job';
```
Then create new steps (existing ones won't retroactively update).
