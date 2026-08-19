(function () {
	"use strict";

	var PLUGIN_KEY = "omega-parallel-process-history";
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
			headers["X-XSRF-TOKEN"] = token;
		}
		return headers;
	}

	function legacyThemeHref() {
		var mode = (document.documentElement.getAttribute("data-mode") || "").toLowerCase();
		var theme = mode === "dark" ? "hpedark" : "default";
		return "/assets/themes/" + theme + "/app.css";
	}

	function escapeHtml(value) {
		return String(value == null ? "" : value)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#39;");
	}

	function apiGet(path) {
		return fetch(path, { headers: buildHeaders(false) }).then(function (response) {
			return response.json();
		});
	}

	function apiPost(path, body) {
		return fetch(path, {
			method: "POST",
			headers: buildHeaders(true),
			body: JSON.stringify(body)
		}).then(function (response) {
			return response.json();
		});
	}

	function formatDate(value) {
		if (!value) {
			return "—";
		}
		var date = new Date(value);
		if (isNaN(date.getTime())) {
			return escapeHtml(value);
		}
		return date.toLocaleString();
	}

	function formatDuration(ms) {
		if (ms == null || isNaN(ms)) {
			return "—";
		}
		var totalSeconds = Math.max(0, Math.round(ms / 1000));
		var hours = Math.floor(totalSeconds / 3600);
		var minutes = Math.floor((totalSeconds % 3600) / 60);
		var seconds = totalSeconds % 60;
		var parts = [];
		if (hours) {
			parts.push(hours + "h");
		}
		if (minutes || hours) {
			parts.push(minutes + "m");
		}
		parts.push(seconds + "s");
		return parts.join(" ");
	}

	function statusMeta(status) {
		var normalized = String(status || "pending").toLowerCase();
		if (normalized === "complete" || normalized === "completed" || normalized === "success") {
			return { css: "status-complete", icon: "✔", text: "Complete" };
		}
		if (normalized === "failed" || normalized === "error") {
			return { css: "status-failed", icon: "✖", text: "Failed" };
		}
		if (normalized === "running" || normalized === "queued" || normalized === "waiting" || normalized === "inprogress") {
			return { css: "status-running", icon: "↻", text: normalized.charAt(0).toUpperCase() + normalized.slice(1) };
		}
		return { css: "status-pending", icon: "•", text: normalized.charAt(0).toUpperCase() + normalized.slice(1) };
	}

	function hasRunningWork(hierarchies) {
		return (hierarchies || []).some(function (hierarchy) {
			return isNodeRunning(hierarchy);
		});
	}

	function isNodeRunning(node) {
		if (!node) return false;
		var process = node.process || node.parent;
		if (process) {
			var status = String(process.status || "").toLowerCase();
			if (status === "running" || status === "queued" || status === "waiting" || status === "inprogress") return true;
			if ((process.events || []).some(function (e) {
				var s = String(e.status || "").toLowerCase();
				return s === "running" || s === "queued" || s === "waiting" || s === "inprogress";
			})) return true;
		}
		return (node.children || []).some(isNodeRunning);
	}

	function styleText() {
		return [
			":host{all:initial;}",
			".omega-parallel-process-history{font-family:var(--hpe-font-family,Arial,sans-serif);color:var(--hpe-color-text-default,#fff);}",
			".parallel-history-actions,.parallel-history-panel{border:1px solid var(--hpe-color-border-weak,rgba(255,255,255,0.12));border-radius:8px;background:var(--hpe-color-background-front,#2a2a2a);padding:16px;margin-bottom:16px;}",
			".parallel-history-header{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:12px;}",
			".parallel-history-header h3{margin:0;font-size:18px;}",
			".parallel-history-subtitle{margin:0;color:var(--hpe-color-text-weak,#c7c7c7);font-size:13px;}",
			".parallel-history-button-row{display:flex;gap:10px;flex-wrap:wrap;align-items:center;}",
			".parallel-history-status{display:inline-flex;align-items:center;gap:8px;padding:6px 10px;border-radius:999px;background:rgba(255,255,255,0.05);font-size:12px;}",
			".parallel-history-status-dot{width:8px;height:8px;border-radius:50%;background:var(--hpe-color-status-ok,#17c964);}",
			".parallel-history-status.busy .parallel-history-status-dot{background:var(--hpe-color-status-warning,#ffbc44);animation:omegaPulse 1.4s infinite ease-in-out;}",
			"@keyframes omegaPulse{0%{opacity:.35;}50%{opacity:1;}100%{opacity:.35;}}",
			".parallel-history-empty{padding:18px;border:1px dashed var(--hpe-color-border-weak,rgba(255,255,255,0.12));border-radius:8px;color:var(--hpe-color-text-weak,#c7c7c7);text-align:center;}",
			".parallel-history-row{border:1px solid var(--hpe-color-border-weak,rgba(255,255,255,0.12));border-radius:8px;padding:12px 16px;margin-bottom:8px;background:var(--hpe-color-background-front,#2a2a2a);}",
			".parallel-history-row.child-process{margin-left:40px;border-left:3px solid var(--hpe-color-border-weak,rgba(255,255,255,0.12));}",
			".parallel-history-row-header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;}",
			".parallel-history-title-wrap{display:flex;align-items:flex-start;gap:12px;min-width:0;}",
			".parallel-history-toggle{background:none;border:0;color:var(--hpe-color-text-default,#fff);padding:0;cursor:pointer;font-size:14px;line-height:1;margin-top:2px;}",
			".status-icon{display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:50%;font-size:12px;font-weight:700;flex:0 0 auto;}",
			".status-complete{background:rgba(23,201,100,.16);color:var(--hpe-color-status-ok,#17c964);}",
			".status-failed{background:rgba(255,64,64,.14);color:var(--hpe-color-status-critical,#ff4040);}",
			".status-running{background:rgba(0,168,255,.14);color:var(--hpe-color-status-info,#00a8ff);}",
			".status-pending{background:rgba(255,188,68,.14);color:var(--hpe-color-status-warning,#ffbc44);}",
			".parallel-history-name{font-weight:600;font-size:15px;margin:0 0 4px;word-break:break-word;}",
			".parallel-history-meta{display:flex;gap:12px;flex-wrap:wrap;color:var(--hpe-color-text-weak,#c7c7c7);font-size:12px;}",
			".parallel-history-badge{display:inline-flex;align-items:center;padding:4px 8px;border-radius:999px;background:rgba(255,255,255,0.05);font-size:11px;text-transform:uppercase;letter-spacing:.04em;}",
			".parallel-history-events{margin-top:12px;margin-left:34px;border-top:1px solid rgba(255,255,255,0.06);padding-top:8px;}",
			".parallel-history-event-row{padding:6px 12px;border-bottom:1px solid rgba(255,255,255,0.05);display:flex;align-items:flex-start;justify-content:space-between;gap:12px;}",
			".parallel-history-event-row:last-child{border-bottom:0;}",
			".parallel-history-event-main{min-width:0;}",
			".parallel-history-event-title{font-size:13px;font-weight:600;margin-bottom:2px;}",
			".parallel-history-event-meta{font-size:12px;color:var(--hpe-color-text-weak,#c7c7c7);}",
			".parallel-history-event-row.event-failed{color:var(--hpe-color-status-critical,#ff4040);}",
			".parallel-history-event-row.event-rollback{font-style:italic;color:var(--hpe-color-status-warning,#ffbc44);}",
			".parallel-history-event-output{margin-top:4px;font-size:12px;color:var(--hpe-color-text-weak,#c7c7c7);white-space:pre-wrap;word-break:break-word;}",
			".parallel-history-tree{margin-top:12px;}",
			".parallel-history-error{color:var(--hpe-color-status-critical,#ff4040);}",
			".parallel-history-action-btn{white-space:nowrap;}",
			".parallel-history-action-group{display:flex;gap:6px;flex-wrap:wrap;align-items:center;}",
			".btn-danger{color:#fff;background:var(--hpe-color-status-critical,#ff4040);border-color:var(--hpe-color-status-critical,#ff4040);}",
			".btn-warning{color:#000;background:var(--hpe-color-status-warning,#ffbc44);border-color:var(--hpe-color-status-warning,#ffbc44);}"
		].join("");
	}

	function template(systemId, systemName) {
		return [
			'<div class="omega-parallel-process-history">',
			'  <div class="parallel-history-actions">',
			'    <div class="parallel-history-header">',
			'      <div>',
			'        <h3>Parallel Process History</h3>',
			'        <p class="parallel-history-subtitle">Hierarchical parent/child process history for <strong>' + escapeHtml(systemName || ("System " + systemId)) + '</strong>.</p>',
			'      </div>',
			'      <div class="parallel-history-button-row">',
			'        <button type="button" class="btn btn-primary" data-action="start-example">Run Example Scenario</button>',
			'        <button type="button" class="btn btn-default" data-action="refresh-history">Refresh History</button>',
			'        <button type="button" class="btn btn-default btn-danger" data-action="clear-history">Clear</button>',
			'        <span class="parallel-history-status" data-field="status-pill"><span class="parallel-history-status-dot"></span><span data-field="status-text">Loading history…</span></span>',
			'      </div>',
			'    </div>',
			'  </div>',
			'  <div class="parallel-history-panel">',
			'    <div class="parallel-history-header">',
			'      <div>',
			'        <h3>Hierarchical Process History</h3>',
			'        <p class="parallel-history-subtitle">Parent orchestration processes appear at the root. Child processes and their event history are indented beneath them.</p>',
			'      </div>',
			'    </div>',
			'    <div data-field="history-container" class="parallel-history-tree"></div>',
			'  </div>',
			'</div>'
		].join("");
	}

	function processRow(process, depth, expanded) {
		var meta = statusMeta(process.status);
		var toggleIcon = expanded ? "▾" : "▸";
		var eventsHtml = expanded ? renderEvents(process.events || [], process.id) : "";
		var indentClass = depth === 0 ? '' : 'child-process';
		var indentStyle = depth > 1 ? ' style="margin-left:' + (depth * 40) + 'px;"' : '';
		return [
			'<div class="parallel-history-row ' + indentClass + '"' + indentStyle + '>',
			'  <div class="parallel-history-row-header">',
			'    <div class="parallel-history-title-wrap">',
			'      <button type="button" class="parallel-history-toggle" data-action="toggle-process" data-process-id="' + escapeHtml(String(process.id)) + '">' + toggleIcon + '</button>',
			'      <span class="status-icon ' + meta.css + '">' + meta.icon + '</span>',
			'      <div>',
			'        <div class="parallel-history-name">' + escapeHtml(process.displayName || ("Process " + process.id)) + '</div>',
			'        <div class="parallel-history-meta">',
			'          <span>Status: ' + escapeHtml(meta.text) + '</span>',
			'          <span>Started: ' + escapeHtml(formatDate(process.startDate)) + '</span>',
			'          <span>Duration: ' + escapeHtml(formatDuration(process.durationMs)) + '</span>',
			'        </div>',
			        process.message ? ('<div class="parallel-history-event-output">' + escapeHtml(process.message) + '</div>') : '',
			        process.error ? ('<div class="parallel-history-event-output parallel-history-error">' + escapeHtml(process.error) + '</div>') : '',
			'      </div>',
			'    </div>',
			'    <span class="parallel-history-badge ' + meta.css + '">' + escapeHtml(meta.text) + '</span>',
			'  </div>',
			   eventsHtml,
			'</div>'
		].join("");
	}

	function renderEvents(events, processId) {
		if (!events.length) {
			return '<div class="parallel-history-events"><div class="parallel-history-event-row"><div class="parallel-history-event-main"><div class="parallel-history-event-meta">No process events recorded yet.</div></div></div></div>';
		}
		return '<div class="parallel-history-events">' + events.map(function (event) {
			var meta = statusMeta(event.status);
			var classes = ['parallel-history-event-row'];
			var evtStatus = String(event.status || '').toLowerCase();
			if (evtStatus === 'failed') {
				classes.push('event-failed');
			}
			var isRollback = event.rollback || (event.eventTitle || '').toLowerCase().indexOf('rollback') === 0;
			if (isRollback) {
				classes.push('event-rollback');
			}

			// Build action buttons based on status and flags
			var actions = [];
			var pid = escapeHtml(String(processId || event.processId || ''));
			var eid = escapeHtml(String(event.id || ''));

			if (evtStatus === 'failed') {
				// Retry button for failed retryable events
				if (event.retryable) {
					actions.push('<button type="button" class="btn btn-primary btn-sm parallel-history-action-btn" data-action="retry-event" data-process-id="' + pid + '" data-event-id="' + eid + '">Retry</button>');
				}
				// Trigger Rollback button for failed non-rollback events
				if (!isRollback) {
					actions.push('<button type="button" class="btn btn-warning btn-sm parallel-history-action-btn" data-action="trigger-rollback" data-process-id="' + pid + '" data-event-title="' + escapeHtml(event.eventTitle || '') + '">Trigger Rollback</button>');
				}
			}

			// Cancel button for running/queued cancelable events
			if (event.cancelable && (evtStatus === 'running' || evtStatus === 'queued' || evtStatus === 'waiting')) {
				actions.push('<button type="button" class="btn btn-danger btn-sm parallel-history-action-btn" data-action="cancel-event" data-process-id="' + pid + '" data-event-id="' + eid + '">Cancel</button>');
			}

			// Run Rollback button for pending rollback steps
			if (isRollback && (evtStatus === 'pending' || evtStatus === 'queued')) {
				actions.push('<button type="button" class="btn btn-default btn-sm parallel-history-action-btn" data-action="run-rollback" data-process-id="' + pid + '" data-event-id="' + eid + '">Run Rollback</button>');
			}

			var actionHtml = actions.join(' ');
			return [
				'<div class="' + classes.join(' ') + '">',
				'  <div class="parallel-history-event-main">',
				'    <div class="parallel-history-event-title">' + escapeHtml(event.eventTitle || event.description || ('Event ' + event.id)) + '</div>',
				'    <div class="parallel-history-event-meta">' + escapeHtml(meta.text) + ' • Started: ' + escapeHtml(formatDate(event.startDate)) + ' • Duration: ' + escapeHtml(formatDuration(event.durationMs)) + '</div>',
				      (event.message && event.message !== event.output) ? ('<div class="parallel-history-event-output"><strong>Message:</strong> ' + escapeHtml(event.message) + '</div>') : '',
				      event.output ? ('<div class="parallel-history-event-output"><strong>Output:</strong> ' + escapeHtml(event.output) + '</div>') : '',
				      event.error ? ('<div class="parallel-history-event-output parallel-history-error"><strong>Error:</strong> ' + escapeHtml(event.error) + '</div>') : '',
				'  </div>',
				   actionHtml ? ('  <div class="parallel-history-action-group">' + actionHtml + '</div>') : '',
				'</div>'
			].join('');
		}).join('') + '</div>';
	}

	function renderHistory(state) {
		var container = state.shadow.querySelector('[data-field="history-container"]');
		if (!state.hierarchies.length) {
			container.innerHTML = '<div class="parallel-history-empty">No hierarchical process history exists for this system yet. Run the example scenario to generate one.</div>';
			return;
		}
		container.innerHTML = state.hierarchies.map(function (hierarchy) {
			return renderNode(hierarchy.parent, 0, state) + renderChildNodes(hierarchy.children || [], 1, state);
		}).join('');
	}

	function renderNode(process, depth, state) {
		if (!process) return '';
		var key = String(process.id);
		var expanded = state.expanded[key] !== false;
		return processRow(process, depth, expanded);
	}

	function renderChildNodes(children, depth, state) {
		return (children || []).map(function (child) {
			var html = renderNode(child, depth, state);
			if (child.children && child.children.length) {
				var key = String(child.id);
				var expanded = state.expanded[key] !== false;
				if (expanded) {
					html += renderChildNodes(child.children, depth + 1, state);
				}
			}
			return html;
		}).join('');
	}

	function setStatus(state, text, busy) {
		state.statusText = text;
		state.isBusy = !!busy;
		var pill = state.shadow.querySelector('[data-field="status-pill"]');
		var textEl = state.shadow.querySelector('[data-field="status-text"]');
		if (textEl) {
			textEl.textContent = text;
		}
		if (pill) {
			pill.classList.toggle('busy', !!busy);
		}
	}

	function scheduleAutoRefresh(state) {
		if (state.pollTimer) {
			window.clearTimeout(state.pollTimer);
			state.pollTimer = null;
		}
		if (!hasRunningWork(state.hierarchies)) {
			return;
		}
		state.pollTimer = window.setTimeout(function () {
			fetchHistory(state, { silent: true });
		}, 5000);
	}

	function initExpandedState(node, state) {
		if (!node) return;
		var key = String(node.id);
		if (state.expanded[key] == null) {
			state.expanded[key] = true;
		}
	}

	function expandChildren(children, state) {
		(children || []).forEach(function (child) {
			initExpandedState(child, state);
			expandChildren(child.children, state);
		});
	}

	function fetchHistory(state, options) {
		options = options || {};
		if (!options.silent) {
			setStatus(state, 'Refreshing history…', true);
		}
		return apiGet('/plugin/process-jobs/parallel-status?systemId=' + encodeURIComponent(state.systemId))
			.then(function (data) {
				if (data && data.success === false) {
					throw new Error(data.msg || 'Unable to load hierarchy status');
				}
				state.hierarchies = data.hierarchies || [];
				state.hierarchies.forEach(function (hierarchy) {
					initExpandedState(hierarchy.parent, state);
					expandChildren(hierarchy.children, state);
				});
				renderHistory(state);
				if (!state.hierarchies.length) {
					setStatus(state, 'No previous run', false);
				} else {
					setStatus(state, 'Last updated ' + new Date().toLocaleTimeString(), hasRunningWork(state.hierarchies));
				}
				scheduleAutoRefresh(state);
				return data;
			})
			.catch(function (error) {
				setStatus(state, 'Failed to load history', false);
				var container = state.shadow.querySelector('[data-field="history-container"]');
				container.innerHTML = '<div class="parallel-history-empty parallel-history-error">' + escapeHtml(error.message || 'Unable to load hierarchy status') + '</div>';
			});
	}

	function mount(root) {
		if (!root) {
			return;
		}
		if (stateByRoot.has(root)) {
			unmount(root);
		}

		var state = {
			systemId: root.dataset ? root.dataset.systemId : null,
			systemName: root.dataset ? root.dataset.systemName : null,
			hierarchies: [],
			expanded: {},
			pollTimer: null,
			isBusy: false,
			statusText: ''
		};
		stateByRoot.set(root, state);

		var shadow = root.shadowRoot || root.attachShadow({ mode: 'open' });
		shadow.innerHTML = '';
		state.shadow = shadow;

		var themeLink = document.createElement('link');
		themeLink.rel = 'stylesheet';
		themeLink.href = legacyThemeHref();
		shadow.appendChild(themeLink);

		var style = document.createElement('style');
		style.textContent = styleText();
		shadow.appendChild(style);

		var container = document.createElement('div');
		container.innerHTML = template(state.systemId, state.systemName);
		shadow.appendChild(container);

		function onClick(event) {
			var target = event.target.closest('[data-action]');
			if (!target) {
				return;
			}
			var action = target.getAttribute('data-action');
			if (action === 'refresh-history') {
				fetchHistory(state);
				return;
			}
			if (action === 'clear-history') {
				target.disabled = true;
				setStatus(state, 'Clearing history…', true);
				apiPost('/plugin/process-jobs/parallel-clear', { systemId: Number(state.systemId) })
					.then(function (data) {
						target.disabled = false;
						if (data.success) {
							state.hierarchies = [];
							state.expanded = {};
							renderHistory(state);
							setStatus(state, 'History cleared. Ready to run a new scenario.', false);
						} else {
							setStatus(state, data.msg || 'Unable to clear history', false);
						}
					})
					.catch(function (error) {
						target.disabled = false;
						setStatus(state, error.message || 'Unable to clear history', false);
					});
				return;
			}
			if (action === 'start-example') {
				target.disabled = true;
				setStatus(state, 'Starting example scenario…', true);
				apiPost('/plugin/process-jobs/start-parallel-processes', { systemId: Number(state.systemId) })
					.then(function (data) {
						target.disabled = false;
						if (data.success) {
							setStatus(state, 'Example scenario started. Loading…', true);
							// Small delay to let DB commits settle, then fetch + schedule refreshes
							window.setTimeout(function () {
								fetchHistory(state);
							}, 1500);
							window.setTimeout(function () {
								fetchHistory(state, { silent: true });
							}, 5000);
							window.setTimeout(function () {
								fetchHistory(state, { silent: true });
							}, 10000);
						}
						setStatus(state, data.msg || 'Unable to start scenario', false);
					})
					.catch(function (error) {
						target.disabled = false;
						setStatus(state, error.message || 'Unable to start scenario', false);
					});
				return;
			}
			if (action === 'toggle-process') {
				var processId = target.getAttribute('data-process-id');
				state.expanded[processId] = !(state.expanded[processId] !== false);
				renderHistory(state);
				return;
			}
			if (action === 'run-rollback') {
				var processIdValue = target.getAttribute('data-process-id');
				var eventIdValue = target.getAttribute('data-event-id');
				target.disabled = true;
				setStatus(state, 'Dispatching rollback step…', true);
				apiPost('/plugin/process-jobs/run', {
					processId: Number(processIdValue),
					eventId: Number(eventIdValue)
				}).then(function (data) {
					target.disabled = false;
					if (data.success) {
						setStatus(state, 'Rollback dispatched. Refreshing…', true);
						window.setTimeout(function () { fetchHistory(state); }, 1500);
					} else {
						setStatus(state, data.msg || 'Unable to dispatch rollback', false);
					}
				}).catch(function (error) {
					target.disabled = false;
					setStatus(state, error.message || 'Unable to dispatch rollback', false);
				});
				return;
			}
			if (action === 'retry-event') {
				var retryProcessId = target.getAttribute('data-process-id');
				var retryEventId = target.getAttribute('data-event-id');
				target.disabled = true;
				setStatus(state, 'Retrying step…', true);
				apiPost('/plugin/process-jobs/retry', {
					processId: Number(retryProcessId),
					eventId: Number(retryEventId)
				}).then(function (data) {
					target.disabled = false;
					if (data.success) {
						setStatus(state, 'Retry dispatched. Refreshing…', true);
						window.setTimeout(function () { fetchHistory(state); }, 2000);
					} else {
						setStatus(state, data.msg || 'Unable to retry step', false);
					}
				}).catch(function (error) {
					target.disabled = false;
					setStatus(state, error.message || 'Unable to retry step', false);
				});
				return;
			}
			if (action === 'cancel-event') {
				var cancelProcessId = target.getAttribute('data-process-id');
				var cancelEventId = target.getAttribute('data-event-id');
				target.disabled = true;
				setStatus(state, 'Cancelling step…', true);
				apiPost('/plugin/process-jobs/end-process', {
					processId: Number(cancelProcessId),
					status: 'cancelled'
				}).then(function (data) {
					target.disabled = false;
					if (data.success) {
						setStatus(state, 'Step cancelled. Refreshing…', true);
						window.setTimeout(function () { fetchHistory(state); }, 1000);
					} else {
						setStatus(state, data.msg || 'Unable to cancel step', false);
					}
				}).catch(function (error) {
					target.disabled = false;
					setStatus(state, error.message || 'Unable to cancel step', false);
				});
				return;
			}
			if (action === 'trigger-rollback') {
				var rbProcessId = target.getAttribute('data-process-id');
				var rbEventTitle = target.getAttribute('data-event-title');
				target.disabled = true;
				setStatus(state, 'Triggering rollback…', true);
				apiPost('/plugin/process-jobs/parallel-trigger-rollback', {
					processId: Number(rbProcessId),
					systemId: Number(state.systemId),
					rollbackTitle: 'Rollback: Reverting ' + (rbEventTitle || 'failed step')
				}).then(function (data) {
					target.disabled = false;
					if (data.success) {
						setStatus(state, 'Rollback triggered. Refreshing…', true);
						window.setTimeout(function () { fetchHistory(state); }, 2000);
					} else {
						setStatus(state, data.msg || 'Unable to trigger rollback', false);
					}
				}).catch(function (error) {
					target.disabled = false;
					setStatus(state, error.message || 'Unable to trigger rollback', false);
				});
				return;
			}
		}

		shadow.addEventListener('click', onClick);
		state.clickHandler = onClick;
		fetchHistory(state);
	}

	function unmount(root) {
		if (!root) {
			return;
		}
		var state = stateByRoot.get(root);
		if (state) {
			if (state.pollTimer) {
				window.clearTimeout(state.pollTimer);
			}
			if (state.clickHandler && state.shadow) {
				state.shadow.removeEventListener('click', state.clickHandler);
			}
			if (state.shadow) {
				state.shadow.innerHTML = '';
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
