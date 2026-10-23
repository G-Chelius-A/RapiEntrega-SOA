package com.api.rapientrega;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RapientregaApplication {

	public static void main(String[] args) {
		SpringApplication.run(RapientregaApplication.class, args);
	}

}
