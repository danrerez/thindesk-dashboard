package br.com.danielcardoso.thindesk.theme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import br.com.danielcardoso.thindesk.config.AppProperties;

@Component
public class ThemeService {

	public static final String DEFAULT_THEME = "default";
	public static final String DEFAULT_LAYOUT = "layouts/main";

	private static final Logger log = LoggerFactory.getLogger(ThemeService.class);

	private final String name;
	private final String layout;

	public ThemeService(AppProperties properties, ResourceLoader resourceLoader) {
		String requested = properties.theme().name();
		if (resourceLoader.getResource("classpath:/static" + stylesheetOf(requested)).exists()) {
			this.name = requested;
		}
		else {
			log.warn("Tema '{}' não encontrado em static/themes. Usando '{}'", requested, DEFAULT_THEME);
			this.name = DEFAULT_THEME;
		}
		String requestedLayout = properties.theme().layout();
		if (resourceLoader.getResource("classpath:/templates/" + requestedLayout + ".html").exists()) {
			this.layout = requestedLayout;
		}
		else {
			log.warn("Layout '{}' não encontrado em templates. Usando '{}'", requestedLayout, DEFAULT_LAYOUT);
			this.layout = DEFAULT_LAYOUT;
		}
	}

	private static String stylesheetOf(String theme) {
		return "/themes/" + theme + "/theme.css";
	}

	public String getName() {
		return name;
	}

	public String getStylesheet() {
		return stylesheetOf(name);
	}

	public String getLayout() {
		return layout;
	}
}
