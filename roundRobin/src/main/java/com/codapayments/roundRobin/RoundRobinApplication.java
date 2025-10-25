package com.codapayments.roundRobin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RoundRobinApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoundRobinApplication.class, args);
	}

}
