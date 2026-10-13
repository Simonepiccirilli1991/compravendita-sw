package it.compravendita.gestionale;

import org.springframework.boot.SpringApplication;

public class TestGestionaleApplication {

	public static void main(String[] args) {
		SpringApplication.from(GestionaleApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
