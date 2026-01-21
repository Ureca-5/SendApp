package com.mycom.myapp.sendapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableScheduling
public class SendappApplication {

	public static void main(String[] args) {
		SpringApplication.run(SendappApplication.class, args);
	}

}
