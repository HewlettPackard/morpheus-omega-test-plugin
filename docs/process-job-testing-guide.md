# Process Job REST Testing Guide

This guide covers how to use the Omega Process Job REST server (`127.0.0.1:8090`) to set up
and manually test process-job features: auto-retry, retry with inputs, and cancelable steps.

## Prerequisites

1. Morpheus UI running locally with the omega test plugin loaded
2. A pre-existing instance/workload (note the workload ID)
3. The omega plugin JAR built and deployed (`./gradlew shadowJar`)

Verify the REST server is running:

```bash
curl -s http://127.0.0.1:8090/process-jobs/health
# {"status":"ok","provider":"omega.process-job"}
```

## Provider Configuration

The `OmegaProcessJobProvider` declares:
- `isRetryable() = true` — steps can be retried after failure
- `getRetryCount() = 3` — auto-retry up to 3 times before landing on `failed`
- `getRetryDelaySeconds() = 10` — wait 10s between auto-retries
- `isCancelable() = true` — steps show a cancel button in the UI while running

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
curl -s -X POST http://127.0.0.1:8090/process-jobs/start-process \
  -H 'Content-Type: application/json' \
  -d '{
    "workloadId": 22,
    "userId": 1,
    "description": "Auto-retry test",
    "stepCount": 1,
    "stepConfigs": [{"simulateFailure": "true", "succeedOnAttempt": 2, "sleepSeconds": 1}]
  }'

# Note the processId and eventId from the response

# Dispatch the step
curl -s -X POST http://127.0.0.1:8090/process-jobs/run \
  -H 'Content-Type: application/json' \
  -d '{"processId": <PROCESS_ID>, "eventId": <EVENT_ID>}'
```

**Expected behavior:**
- Step fails on attempt 0 → platform schedules retry after 10s
- Step fails on attempt 1 → platform schedules retry after 10s
- Step succeeds on attempt 2 → status becomes `complete`
- Total time: ~25s (1s execution × 3 attempts + 10s delay × 2 retries)

**Verify in UI:** Navigate to Operations > History, find the process. Should show retry messages.

---

### 2. Retry with Inputs (Manual Retry from UI)

**Goal:** Set up a step that exhausts auto-retries and lands on `failed` status, so the UI
shows a "Retry" button with the provider's option types as a form.

```bash
# Create a step that always fails (succeedOnAttempt=0 means never auto-succeed)
curl -s -X POST http://127.0.0.1:8090/process-jobs/start-process \
  -H 'Content-Type: application/json' \
  -d '{
    "workloadId": 22,
    "userId": 1,
    "description": "Retry with inputs test",
    "stepCount": 1,
    "stepConfigs": [{"simulateFailure": "true", "succeedOnAttempt": 0, "sleepSeconds": 1}]
  }'

# Dispatch the step
curl -s -X POST http://127.0.0.1:8090/process-jobs/run \
  -H 'Content-Type: application/json' \
  -d '{"processId": <PROCESS_ID>, "eventId": <EVENT_ID>}'
```

**Wait ~40 seconds** for all 3 auto-retries to exhaust (3 × ~11s each).

**Expected final state:** Event status = `failed`, `retryable = true`

**Manual testing in UI:**
1. Navigate to the process in Operations > History
2. The failed step should show a **"Retry"** button
3. Clicking it should present a form with the provider's option types:
   - Simulate Failure (checkbox)
   - Succeed On Attempt (number)
   - Sleep Seconds (number)
   - Output Message (text)
4. Uncheck "Simulate Failure" and submit — step should succeed on retry

---

### 3. Cancelable Step

**Goal:** Set up a long-running step that is cancelable, so the UI shows a "Cancel" button.

```bash
# Create a step with a long sleep (300s gives plenty of time to test the cancel button)
curl -s -X POST http://127.0.0.1:8090/process-jobs/start-process \
  -H 'Content-Type: application/json' \
  -d '{
    "workloadId": 22,
    "userId": 1,
    "description": "Cancelable step test",
    "stepCount": 1,
    "stepConfigs": [{"sleepSeconds": 300}]
  }'

# Dispatch the step
curl -s -X POST http://127.0.0.1:8090/process-jobs/run \
  -H 'Content-Type: application/json' \
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

### POST /process-jobs/start-process

Creates a new process on an existing workload and optionally inserts steps.

**Body:**
```json
{
  "workloadId": 22,
  "userId": 1,
  "description": "Test process",
  "stepCount": 2,
  "stepConfigs": [
    {"simulateFailure": "true", "sleepSeconds": 1},
    {"sleepSeconds": 5, "outputMessage": "Step 2 done"}
  ]
}
```

**Response:**
```json
{
  "success": true,
  "processId": 330,
  "steps": [
    {"eventId": 669, "stepTitle": "Omega Step 1"},
    {"eventId": 670, "stepTitle": "Omega Step 2"}
  ],
  "msg": "Process started for workload 22 with 2 step(s)"
}
```

### POST /process-jobs/run

Dispatches an existing process step for execution via the RabbitMQ pipeline.

**Body:**
```json
{"processId": 330, "eventId": 669}
```

### POST /process-jobs/retry

Re-dispatches a failed step. Note: config overrides in the body are set on the DTO
but the platform re-reads the DB record for execution, so modifying inputs must be done
via the UI retry flow (which uses `stepInputs`).

**Body:**
```json
{"processId": 330, "eventId": 669, "config": {"simulateFailure": "false"}}
```

### GET /process-jobs/status?processId=330

Returns process details and event list.

### GET /process-jobs/health

Returns server status.

---

## Troubleshooting

### Step stuck in `queued`

The process job worker is single-threaded per job type. If a previous step is still
`running` (e.g., a long `Thread.sleep()`), new steps queue behind it. Solutions:
- Wait for the running step to complete
- Force-complete the blocking event in the DB:
  ```sql
  UPDATE process_event SET status='cancelled' WHERE id=<blocking_event_id>;
  ```
- Restart the Morpheus application (clears stuck threads)

### `/run` returns `success: false`

- Verify the processId and eventId exist in the database
- Check that the event status is `pending` or `queued` (not already `running` or `complete`)
- Ensure the `ProcessJobType` seed record exists: 
  ```sql
  SELECT * FROM process_job_type WHERE code='omega.process-job';
  ```

### Cancelable flag not set on new events

The `cancelable` flag is propagated from `ProcessJobType` at insert time. If it's not set:
```sql
UPDATE process_job_type SET cancelable=1 WHERE code='omega.process-job';
```
Then create new steps (existing ones won't retroactively update).
