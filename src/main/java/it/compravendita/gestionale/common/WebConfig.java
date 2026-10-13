package it.compravendita.gestionale.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class WebConfig implements WebMvcConfigurer {

	private final AppProperties appProperties;

	public WebConfig(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	/** Espone i file caricati (foto articoli) sotto /media/**. */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String cartella = appProperties.uploadDir().toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler(FileStorage.URL_PREFIX + "**")
			.addResourceLocations(cartella.endsWith("/") ? cartella : cartella + "/");
	}

}
