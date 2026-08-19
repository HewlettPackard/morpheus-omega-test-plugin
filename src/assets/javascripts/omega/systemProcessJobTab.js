/*
 * Omega System Process Job tab bundle.
 *
 * Conforms to the Morpheus React system-tab mount contract (see
 * morpheus-react/src/pages/Infrastructure/Systems/tabs.jsx). The React host injects this
 * tab's HTML via innerHTML (which does NOT execute inline scripts), finds the
 * [data-plugin-react-root] element + the <script src> for this bundle, loads the bundle,
 * then calls window.Morpheus.pluginTabs[pluginKey][entry](root) to mount, and
 * window.Morpheus.pluginTabs[pluginKey].unmount(root) to tear down.
 *
 * This is the System-scoped counterpart of the legacy instance processJobTab UI. It starts a
 * process against the current System (refType='system', no workload) via the new
 * MorpheusProcessService#startProcess(System, ...) overload, then lets the user add and run
 * individual process steps to exercise the MORPH-6233 step/job flow.
 */
(function () {
	"use strict";

	var PLUGIN_KEY = "omega-system-process-job";

	// Per-root state so multiple mounts / remounts stay isolated.
	var stateByRoot = new WeakMap();

	function buildHeaders(json) {
		var headers = {};
		if (json) {
			headers["Content-Type"] = "application/json";
		}
		var tokenEl = document.querySelector('meta[name="_csrf"]');
		var headerEl = document.querySelector('meta[name="_csrf_header"]');
		var token = tokenEl ? tokenEl.getAttribute("content") : "";
		var headerName = headerEl ? headerEl.getAttribute("content") : "";
		if (token && headerName) {
			headers[headerName] = token;
		} else if (token) {
			// Fallback for environments exposing only the token meta.
			headers["X-XSRF-TOKEN"] = token;
		}
		return headers;
	}

	// The System detail page is a React/Focus UI page that does NOT load the legacy Morpheus theme
	// stylesheet, so the legacy component classes (.btn, .info-section, .form-control, ...) used by
	// this tab only resolve if we pull that stylesheet in ourselves. We do that inside a Shadow DOM
	// (see mount) so the global Bootstrap rules stay scoped to this tab. Pick the theme bundle that
	// matches the page's current light/dark mode.
	function legacyThemeHref() {
		var mode = (document.documentElement.getAttribute("data-mode") || "").toLowerCase();
		var theme = mode === "dark" ? "hpedark" : "default";
		return "/assets/themes/" + theme + "/app.css";
	}

	function escapeHtml(value) {
		return String(value)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#39;");
	}

	function template(systemId, systemName) {
		var safeName = systemName ? String(systemName) : ("System " + systemId);
		return [
			'<div class="omega-system-process-job">',
			'	<div class="info-section">',
			'		<div class="info-title">System Process Job Test Runner</div>',
			'		<div class="info-detail">',
			'			<p class="help-block">Create a system-scoped process (refType=system, no workload) for <strong>',
			escapeHtml(safeName), '</strong> (ID: ', escapeHtml(String(systemId)),
			'), then add and run individual steps to test execution, retry and cancel features.</p>',
			'		</div>',
			'	</div>',
			'	<div class="info-section" data-section="start">',
			'		<div class="info-title">1. Start Process</div>',
			'		<div class="info-detail">',
			'			<div class="row">',
			'				<div class="col-sm-6">',
			'					<h5>New System Process</h5>',
			'					<div class="form-group">',
			'						<button type="button" data-action="start-process" class="btn btn-primary">Start System Process</button>',
			'					</div>',
			'				</div>',
			'				<div class="col-sm-6">',
			'					<h5>Use Existing Process</h5>',
			'					<div class="form-group">',
			'						<label>Process ID</label>',
			'						<input type="number" data-field="existing-process-id" class="form-control" placeholder="Enter process ID">',
			'					</div>',
			'					<div class="form-group">',
			'						<button type="button" data-action="use-process" class="btn btn-default">Use Selected Process</button>',
			'					</div>',
			'				</div>',
			'			</div>',
			'			<div class="form-group">',
			'				<button type="button" data-action="end-process" class="btn btn-default" disabled>End Process</button>',
			'				<span data-field="process-info" class="help-inline"></span>',
			'			</div>',
			'		</div>',
			'	</div>',
			'	<div class="info-section" data-section="steps">',
			'		<div class="info-title">2. Add Steps</div>',
			'		<div class="info-detail">',
			'			<div class="row" data-field="step-config-row">',
			'				<div class="col-sm-1"><div class="form-group"><label>Simulate Failure</label><div style="margin-top:6px;"><input type="checkbox" data-field="simulate-failure" disabled></div></div></div>',
			'				<div class="col-sm-1"><div class="form-group"><label>User Retryable</label><div style="margin-top:6px;"><input type="checkbox" data-field="retryable" checked disabled></div></div></div>',
			'				<div class="col-sm-1"><div class="form-group"><label>Retries</label><input type="number" data-field="retry-count" value="3" min="0" max="10" class="form-control" disabled></div></div>',
			'				<div class="col-sm-1"><div class="form-group"><label>User Cancelable</label><div style="margin-top:6px;"><input type="checkbox" data-field="cancelable" checked disabled></div></div></div>',
			'				<div class="col-sm-2"><div class="form-group"><label>Succeed On Attempt</label><input type="number" data-field="succeed-on-attempt" value="0" min="0" class="form-control" disabled></div></div>',
			'				<div class="col-sm-2"><div class="form-group"><label>Sleep Seconds</label><input type="number" data-field="sleep-seconds" value="5" min="1" class="form-control" disabled></div></div>',
			'				<div class="col-sm-3"><div class="form-group"><label>Message</label><input type="text" data-field="output-message" value="" class="form-control" placeholder="(optional)" disabled></div></div>',
			'			</div>',
			'			<div class="row">',
			'				<div class="col-sm-4"><div class="form-group"><label>Output</label><input type="text" data-field="step-output" value="" class="form-control" placeholder="(optional)" disabled></div></div>',
			'				<div class="col-sm-4"><div class="form-group"><label>Error Message</label><input type="text" data-field="step-error" value="" class="form-control" placeholder="(optional — shown on failure)" disabled></div></div>',
			'				<div class="col-sm-2"><div class="form-group"><label>&nbsp;</label><div><button type="button" data-action="add-step" class="btn btn-default" disabled>+ Add Step</button></div></div></div>',
			'			</div>',
			'			<table data-field="steps-table" class="table" style="display:none;">',
			'				<thead><tr><th>#</th><th>Event ID</th><th>Config</th><th>Status</th><th>Action</th></tr></thead>',
			'				<tbody data-field="steps-body"></tbody>',
			'			</table>',
			'		</div>',
			'	</div>',
			'	<div class="info-section">',
			'		<div class="info-title">Log</div>',
			'		<div class="info-detail"><pre data-field="log-output" class="code-block">Ready.</pre></div>',
			'	</div>',
			'</div>'
		].join("");
	}

	function mount(root) {
		if (!root) {
			return;
		}
		// Idempotent: tear down any prior mount on this root first.
		if (stateByRoot.has(root)) {
			unmount(root);
		}

		var systemId = root.dataset ? root.dataset.systemId : null;
		var systemName = root.dataset ? root.dataset.systemName : null;

		var state = {
			systemId: systemId,
			processId: null,
			steps: [],
			timers: []
		};
		stateByRoot.set(root, state);

		// Render inside a Shadow DOM so we can load the legacy Morpheus theme stylesheet and reuse its
		// real component classes without the global Bootstrap rules leaking out and clobbering the
		// surrounding React/Focus UI page. Inherited --hpe-* design tokens still cross the boundary.
		var shadow = root.shadowRoot || root.attachShadow({ mode: "open" });
		shadow.innerHTML = "";

		var themeLink = document.createElement("link");
		themeLink.rel = "stylesheet";
		themeLink.href = legacyThemeHref();
		shadow.appendChild(themeLink);

		// The legacy theme styles <pre> (the log box) light-gray via Bootstrap. Override just that one
		// element to match the dark React shell using the page's own --hpe-* design tokens (custom
		// properties inherit across the shadow boundary). Scoped to this shadow root, so nothing leaks.
		var logStyle = document.createElement("style");
		logStyle.textContent = ".code-block{"
			+ "background:var(--hpe-color-background-back,#1c1c1c);"
			+ "color:var(--hpe-color-text-default,#fff);"
			+ "border:1px solid var(--hpe-color-border-weak,rgba(255,255,255,0.12));"
			+ "border-radius:var(--hpe-radius-small,8px);"
			+ "padding:12px 14px;max-height:280px;overflow:auto;"
			+ "font-family:var(--hpe-text-code-fontFamily,SFMono-Regular,Menlo,Consolas,monospace);"
			+ "font-size:12.5px;line-height:1.6;}";
		shadow.appendChild(logStyle);

		var container = document.createElement("div");
		container.innerHTML = template(systemId, systemName);
		shadow.appendChild(container);

		state.shadow = shadow;

		var q = function (selector) {
			return shadow.querySelector(selector);
		};
		var qa = function (selector) {
			return Array.prototype.slice.call(shadow.querySelectorAll(selector));
		};

		var logEl = q('[data-field="log-output"]');
		var stepsTable = q('[data-field="steps-table"]');
		var stepsBody = q('[data-field="steps-body"]');
		var processInfo = q('[data-field="process-info"]');

		function log(msg) {
			var ts = new Date().toLocaleTimeString();
			logEl.textContent += "\n[" + ts + "] " + msg;
			logEl.scrollTop = logEl.scrollHeight;
		}

		function apiPost(path, body) {
			return fetch("/plugin/process-jobs/" + path, {
				method: "POST",
				headers: buildHeaders(true),
				body: JSON.stringify(body)
			}).then(function (r) {
				return r.json();
			});
		}

		function enableStepInputs(enabled) {
			qa('[data-section="steps"] input, [data-section="steps"] button').forEach(function (el) {
				el.disabled = !enabled;
			});
		}

		function renderSteps() {
			stepsTable.style.display = state.steps.length ? "table" : "none";
			stepsBody.innerHTML = "";
			state.steps.forEach(function (step, idx) {
				var tr = document.createElement("tr");
				var configStr = [];
				if (step.config.simulateFailure === "true") configStr.push("fail");
				if (step.config.isRetryable === "true") configStr.push("retry(" + (step.config.retryCount || 3) + ")");
				if (step.config.isCancelable === "true") configStr.push("cancel");
				if (step.config.succeedOnAttempt > 0) configStr.push("succeed@" + step.config.succeedOnAttempt);
				configStr.push(step.config.sleepSeconds + "s");
				if (step.config.outputMessage) configStr.push('"' + step.config.outputMessage + '"');

				var statusClass = "";
				if (step.status === "complete") statusClass = "text-success";
				else if (step.status === "failed") statusClass = "text-danger";
				else if (step.status === "running" || step.status === "queued") statusClass = "text-info";
				else if (step.status === "waiting") statusClass = "text-warning";

				var action = "";
				if (step.status === "pending" || step.status === "failed") {
					action = '<button class="btn btn-primary btn-sm" data-action="run-step" data-idx="' + idx + '">Run</button>';
				} else if (step.status === "running" || step.status === "waiting" || step.status === "queued") {
					action = '<button class="btn btn-default btn-sm" data-action="refresh" data-idx="' + idx + '">Refresh</button>';
				}

				tr.innerHTML = "<td>" + (idx + 1) + "</td>"
					+ "<td>" + step.eventId + "</td>"
					+ "<td><small>" + escapeHtml(configStr.join(", ")) + "</small></td>"
					+ '<td class="' + statusClass + '">' + escapeHtml(step.status) + "</td>"
					+ "<td>" + action + "</td>";
				stepsBody.appendChild(tr);
			});
		}

		function activateProcess(id) {
			state.processId = id;
			processInfo.textContent = "Process ID: " + id;
			processInfo.className = "help-inline text-success";
			enableStepInputs(true);
			q('[data-action="start-process"]').disabled = true;
			q('[data-action="use-process"]').disabled = true;
			q('[data-action="end-process"]').disabled = false;
		}

		function deactivateProcess() {
			state.processId = null;
			state.steps = [];
			processInfo.textContent = "";
			processInfo.className = "help-inline";
			enableStepInputs(false);
			q('[data-action="start-process"]').disabled = false;
			q('[data-action="use-process"]').disabled = false;
			q('[data-action="end-process"]').disabled = true;
			renderSteps();
		}

		function scheduleRefresh(delayMs) {
			var id = window.setTimeout(refreshStatus, delayMs);
			state.timers.push(id);
		}

		function refreshStatus() {
			if (!state.processId) {
				return;
			}
			fetch("/api/processes/" + state.processId, { headers: buildHeaders(false) })
				.then(function (r) { return r.json(); })
				.then(function (data) {
					if (data.process && data.process.events) {
						data.process.events.forEach(function (evt) {
							var match = state.steps.find(function (s) { return s.eventId === evt.id; });
							if (match) {
								match.status = evt.status || match.status;
							}
						});
						renderSteps();
						log("Status refreshed.");
					}
				})
				.catch(function (err) { log("Refresh error: " + err.message); });
		}

		function runStep(idx) {
			var step = state.steps[idx];
			if (!step) {
				return;
			}
			log("Running step " + (idx + 1) + " (event " + step.eventId + ")...");
			step.status = "queued";
			renderSteps();

			apiPost("run", { processId: state.processId, eventId: step.eventId })
				.then(function (data) {
					if (data.success) {
						step.status = "running";
						log("Step " + (idx + 1) + " dispatched. Auto-refreshing...");
						scheduleRefresh(3000);
						scheduleRefresh(8000);
						scheduleRefresh(15000);
					} else {
						step.status = "failed";
						log("ERROR dispatching step " + (idx + 1) + ": " + (data.msg || "unknown"));
					}
					renderSteps();
				})
				.catch(function (err) {
					step.status = "failed";
					log("ERROR: " + err.message);
					renderSteps();
				});
		}

		// --- Event delegation (single listener, scoped to root) ---
		function onClick(evt) {
			var target = evt.target.closest("[data-action]");
			if (!target) {
				return;
			}
			var action = target.getAttribute("data-action");

			if (action === "start-process") {
				target.disabled = true;
				log("Starting system process for system " + state.systemId + "...");
				apiPost("start-system-process", { systemId: Number(state.systemId), stepConfigs: [] })
					.then(function (data) {
						if (data.success) {
							activateProcess(data.processId);
							log("System process " + data.processId + " started (refType=system, refId=" + state.systemId + ").");
						} else {
							log("ERROR: " + (data.msg || "Failed to start process"));
							target.disabled = false;
						}
					})
					.catch(function (err) {
						log("ERROR: " + err.message);
						target.disabled = false;
					});
			} else if (action === "use-process") {
				var id = parseInt(q('[data-field="existing-process-id"]').value, 10);
				if (!id) {
					log("ERROR: Enter a valid process ID");
					return;
				}
				target.disabled = true;
				log("Loading process " + id + "...");
				fetch("/api/processes/" + id, { headers: buildHeaders(false) })
					.then(function (r) { return r.json(); })
					.then(function (data) {
						if (data.process) {
							var status = data.process.status;
							if (status === "complete" || status === "failed" || status === "cancelled") {
								log("ERROR: Process " + id + " is in terminal state: " + status);
								target.disabled = false;
								return;
							}
							activateProcess(id);
							if (data.process.events) {
								data.process.events.forEach(function (evt) {
									state.steps.push({
										eventId: evt.id,
										config: evt.config || {},
										status: evt.status || "pending"
									});
								});
								renderSteps();
							}
							log("Using existing process " + id + " (status: " + status + ", " + state.steps.length + " existing steps)");
						} else {
							log("ERROR: Process " + id + " not found");
							target.disabled = false;
						}
					})
					.catch(function (err) {
						log("ERROR: " + err.message);
						target.disabled = false;
					});
			} else if (action === "end-process") {
				if (!state.processId) {
					return;
				}
				target.disabled = true;
				log("Ending process " + state.processId + "...");
				apiPost("end-process", { processId: state.processId })
					.then(function (data) {
						if (data.success) {
							log("Process " + state.processId + " ended.");
							deactivateProcess();
						} else {
							log("ERROR: " + (data.msg || "Failed to end process"));
							target.disabled = false;
						}
					})
					.catch(function (err) {
						log("ERROR: " + err.message);
						target.disabled = false;
					});
			} else if (action === "add-step") {
				if (!state.processId) {
					return;
				}
				var config = {
					simulateFailure: q('[data-field="simulate-failure"]').checked ? "true" : "false",
					isRetryable: q('[data-field="retryable"]').checked ? "true" : "false",
					retryCount: parseInt(q('[data-field="retry-count"]').value, 10) || 3,
					isCancelable: q('[data-field="cancelable"]').checked ? "true" : "false",
					succeedOnAttempt: parseInt(q('[data-field="succeed-on-attempt"]').value, 10) || 0,
					sleepSeconds: parseInt(q('[data-field="sleep-seconds"]').value, 10) || 5
				};
				var msg = q('[data-field="output-message"]').value;
				if (msg) {
					config.outputMessage = msg;
				}
				var stepOutput = q('[data-field="step-output"]').value;
				if (stepOutput) {
					config.stepOutput = stepOutput;
				}
				var stepError = q('[data-field="step-error"]').value;
				if (stepError) {
					config.stepError = stepError;
				}
				target.disabled = true;
				log("Adding step to process " + state.processId + "...");
				apiPost("add-steps", { processId: state.processId, systemId: Number(state.systemId), stepConfigs: [config] })
					.then(function (data) {
						target.disabled = false;
						if (data.success && data.steps && data.steps.length > 0) {
							var newStep = data.steps[data.steps.length - 1];
							state.steps.push({ eventId: newStep.eventId, config: config, status: "pending" });
							log("Step added: event " + newStep.eventId);
							renderSteps();
						} else {
							log("ERROR: " + (data.msg || "Failed to add step"));
						}
					})
					.catch(function (err) {
						target.disabled = false;
						log("ERROR: " + err.message);
					});
			} else if (action === "run-step") {
				runStep(parseInt(target.getAttribute("data-idx"), 10));
			} else if (action === "refresh") {
				refreshStatus();
			}
		}

		shadow.addEventListener("click", onClick);
		state.clickHandler = onClick;
		state.clickTarget = shadow;
	}

	function unmount(root) {
		if (!root) {
			return;
		}
		var state = stateByRoot.get(root);
		if (state) {
			state.timers.forEach(function (id) { window.clearTimeout(id); });
			if (state.clickHandler && state.clickTarget) {
				state.clickTarget.removeEventListener("click", state.clickHandler);
			}
			if (state.shadow) {
				state.shadow.innerHTML = "";
			}
			stateByRoot.delete(root);
		}
	}

	window.Morpheus = window.Morpheus || {};
	window.Morpheus.pluginTabs = window.Morpheus.pluginTabs || {};
	window.Morpheus.pluginTabs[PLUGIN_KEY] = {
		mount: mount,
		unmount: unmount
	};
})();
