package com.cookgenie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CookgenieApplication {

	public static void main(String[] args) {
		SpringApplication.run(CookgenieApplication.class, args);
	}

}
