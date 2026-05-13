package com.morpheusdata.omega.processjob

import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.Renderer
import com.morpheusdata.views.ViewModel
import com.github.jknack.handlebars.io.TemplateLoader

/**
 * No-op renderer that satisfies the hasCustomRender() check in PluginManager.
 * Since our PluginController only returns JsonResponse, no template rendering is needed.
 */
class NoOpRenderer implements Renderer<Void> {

	@Override
	HTMLResponse render(String template, ViewModel<?> model) {
		return null
	}

	@Override
	HTMLResponse renderTemplate(String location, ViewModel<?> model) {
		return null
	}

	@Override
	Iterable<TemplateLoader> getTemplateLoaders() {
		return []
	}

	@Override
	void addTemplateLoader(ClassLoader loader) {
		// no-op
	}

	@Override
	void removeTemplateLoader(ClassLoader loader) {
		// no-op
	}

	@Override
	Void getEngine() {
		return null
	}
}
