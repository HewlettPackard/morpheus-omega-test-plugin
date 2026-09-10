/*
 * Omega GPU Inventory server tab bundle.
 *
 * Uses the Morpheus React plugin-tab mount contract and renders inside a Shadow DOM so the
 * legacy theme stylesheet remains scoped to this tab.
 */
(function () {
	"use strict";

	var PLUGIN_KEY = "omega-gpu-inventory";
	var DEVICE_FIELDS = [
		"uniqueId",
		"kind",
		"vendor",
		"name",
		"uuid",
		"pciBusId",
		"parentUniqueId",
		"vmId",
		"vgpuType",
		"vgpuTypeId",
		"vendorId",
		"productId"
	];
	var stateByRoot = new WeakMap();

	function buildHeaders() {
		var headers = { "Content-Type": "application/json" };
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
		return "/assets/themes/" + (mode === "dark" ? "hpedark" : "default") + "/app.css";
	}

	function template() {
		return [
			'<div class="omega-gpu-inventory">',
			'	<div class="info-section">',
			'		<div class="info-title">GPU Inventory API Test</div>',
			'		<div class="info-detail">',
			'			<p class="help-block" data-field="server-info"></p>',
			'			<div class="row">',
			'				<div class="col-sm-4">',
			'					<div class="form-group">',
			'						<label for="omega-gpu-mode">API mode</label>',
			'						<select id="omega-gpu-mode" data-field="mode" class="form-control">',
			'							<option value="sync">Sync</option>',
			'							<option value="async">Async</option>',
			'							<option value="both" selected>Both</option>',
			'						</select>',
			'					</div>',
			'				</div>',
			'				<div class="col-sm-4">',
			'					<div class="form-group">',
			'						<label>&nbsp;</label>',
			'						<div><button type="button" data-action="load" class="btn btn-primary">Get GPU Inventory</button></div>',
			'					</div>',
			'				</div>',
			'			</div>',
			'			<div data-field="status" class="help-block" role="status" aria-live="polite">Ready.</div>',
			'			<div data-field="parity" class="alert" role="status" style="display:none;"></div>',
			'		</div>',
			'	</div>',
			'	<div data-field="results"></div>',
			'</div>'
		].join("");
	}

	function displayValue(value) {
		if (value === null) {
			return "null";
		}
		if (value === undefined) {
			return "";
		}
		if (typeof value === "object") {
			return JSON.stringify(value);
		}
		return String(value);
	}

	function appendTextElement(parent, tagName, className, text) {
		var element = document.createElement(tagName);
		if (className) {
			element.className = className;
		}
		element.textContent = text;
		parent.appendChild(element);
		return element;
	}

	function renderResult(resultsEl, label, result) {
		var section = document.createElement("div");
		section.className = "info-section";
		appendTextElement(section, "div", "info-title", label + " result");

		var detail = document.createElement("div");
		detail.className = "info-detail";
		section.appendChild(detail);

		var summary = document.createElement("p");
		summary.className = result.success ? "text-success" : "text-danger";
		summary.textContent = "success=" + displayValue(result.success)
			+ " | deviceCount=" + displayValue(result.deviceCount)
			+ " | msg=" + displayValue(result.msg);
		detail.appendChild(summary);

		if (result.errors && Object.keys(result.errors).length) {
			appendTextElement(detail, "pre", "code-block", JSON.stringify(result.errors, null, 2));
		}

		var tableWrap = document.createElement("div");
		tableWrap.className = "omega-table-wrap";
		var table = document.createElement("table");
		table.className = "table table-striped table-condensed";
		var thead = document.createElement("thead");
		var headerRow = document.createElement("tr");
		DEVICE_FIELDS.forEach(function (field) {
			appendTextElement(headerRow, "th", "", field);
		});
		thead.appendChild(headerRow);
		table.appendChild(thead);

		var tbody = document.createElement("tbody");
		(result.devices || []).forEach(function (device) {
			var row = document.createElement("tr");
			DEVICE_FIELDS.forEach(function (field) {
				appendTextElement(row, "td", "", displayValue(device[field]));
			});
			tbody.appendChild(row);
		});
		table.appendChild(tbody);
		tableWrap.appendChild(table);
		detail.appendChild(tableWrap);

		if (!result.devices || result.devices.length === 0) {
			appendTextElement(detail, "p", "help-block", "No GPU devices returned.");
		}
		resultsEl.appendChild(section);
	}

	function mount(root) {
		if (!root) {
			return;
		}
		if (stateByRoot.has(root)) {
			unmount(root);
		}

		var state = { requestController: null };
		stateByRoot.set(root, state);

		var shadow = root.shadowRoot || root.attachShadow({ mode: "open" });
		shadow.innerHTML = "";
		state.shadow = shadow;

		var themeLink = document.createElement("link");
		themeLink.rel = "stylesheet";
		themeLink.href = legacyThemeHref();
		shadow.appendChild(themeLink);

		var style = document.createElement("style");
		style.textContent = [
			".omega-table-wrap{overflow-x:auto;}",
			".omega-table-wrap table{min-width:1500px;}",
			".omega-table-wrap th,.omega-table-wrap td{white-space:nowrap;}",
			".code-block{max-height:180px;overflow:auto;}",
			"[data-field=parity]{margin-top:12px;}"
		].join("");
		shadow.appendChild(style);

		var container = document.createElement("div");
		container.innerHTML = template();
		shadow.appendChild(container);

		var serverId = root.dataset ? root.dataset.serverId : "";
		var serverName = root.dataset ? root.dataset.serverName : "";
		var serverInfo = shadow.querySelector('[data-field="server-info"]');
		serverInfo.textContent = "Exercise live GPU inventory for "
			+ (serverName || ("server " + serverId)) + " (ID: " + serverId + ").";

		var button = shadow.querySelector('[data-action="load"]');
		var modeEl = shadow.querySelector('[data-field="mode"]');
		var statusEl = shadow.querySelector('[data-field="status"]');
		var parityEl = shadow.querySelector('[data-field="parity"]');
		var resultsEl = shadow.querySelector('[data-field="results"]');

		function setStatus(message, isError) {
			statusEl.textContent = message;
			statusEl.className = isError ? "help-block text-danger" : "help-block";
		}

		function onClick() {
			if (state.requestController) {
				state.requestController.abort();
			}
			state.requestController = new AbortController();
			button.disabled = true;
			resultsEl.textContent = "";
			parityEl.style.display = "none";
			setStatus("Loading GPU inventory...", false);

			fetch("/plugin/compute-hosts/gpu-devices", {
				method: "POST",
				headers: buildHeaders(),
				body: JSON.stringify({
					serverId: Number(serverId),
					mode: modeEl.value
				}),
				signal: state.requestController.signal
			}).then(function (response) {
				return response.json().then(function (data) {
					return { ok: response.ok, status: response.status, data: data };
				});
			}).then(function (response) {
				if (!response.ok) {
					throw new Error(response.data.msg || ("Request failed with HTTP " + response.status));
				}

				var data = response.data;
				Object.keys(data.results || {}).forEach(function (key) {
					renderResult(resultsEl, key.toUpperCase(), data.results[key]);
				});

				if (Object.prototype.hasOwnProperty.call(data, "parity")) {
					parityEl.style.display = "block";
					parityEl.className = data.parity ? "alert alert-success" : "alert alert-danger";
					parityEl.textContent = data.parity
						? "Parity: sync and async normalized results match."
						: "Parity: sync and async normalized results differ.";
				}

				var failed = Object.keys(data.results || {}).some(function (key) {
					return data.results[key].success !== true;
				});
				setStatus(failed ? "Request completed with API errors." : "GPU inventory loaded.", failed);
			}).catch(function (error) {
				if (error.name !== "AbortError") {
					setStatus("Error: " + error.message, true);
				}
			}).finally(function () {
				button.disabled = false;
				state.requestController = null;
			});
		}

		button.addEventListener("click", onClick);
		state.button = button;
		state.clickHandler = onClick;
	}

	function unmount(root) {
		if (!root) {
			return;
		}
		var state = stateByRoot.get(root);
		if (state) {
			if (state.requestController) {
				state.requestController.abort();
			}
			if (state.button && state.clickHandler) {
				state.button.removeEventListener("click", state.clickHandler);
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

	document.querySelectorAll('[data-plugin-key="' + PLUGIN_KEY + '"]').forEach(function (root) {
		mount(root);
	});
})();
