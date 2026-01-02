package com.mushroom.stockkeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class MoStockKeeperApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoStockKeeperApplication.class, args);
	}

}
