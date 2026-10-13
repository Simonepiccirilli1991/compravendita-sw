package it.compravendita.gestionale.common;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietà applicative con prefisso {@code app}.
 *
 * @param uploadDir cartella dove vengono salvate foto e allegati
 */
@ConfigurationProperties("app")
public record AppProperties(Path uploadDir) {

	public AppProperties {
		if (uploadDir == null) {
			uploadDir = Path.of("data", "uploads");
		}
	}

}
