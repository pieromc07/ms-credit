package com.nttdata.mscredit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsCreditApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsCreditApplication.class, args);
    }

}
