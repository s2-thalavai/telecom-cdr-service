package com.telecom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TelecomCdrApplication {

    public static void main(String[] args) {
        // JDBC batch inserts (java.sql.Timestamp) and JPA reads must agree on the zone.
        // Tests, the Maven plugin and the Docker image also pass -Duser.timezone=UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TelecomCdrApplication.class, args);
    }
}
