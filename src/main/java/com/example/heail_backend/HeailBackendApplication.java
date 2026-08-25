package com.example.heail_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class HeailBackendApplication {

	public static void main(String[] args) {
		// The whole app uses timezone-naive LocalDateTime.now() throughout
		// (order timestamps, invoice dates, pulse deadlines, @Scheduled cron
		// jobs), which resolves against the JVM's default timezone — left
		// unset, that's whatever the underlying OS/container defaults to
		// (UTC on EC2/Docker, not IST), so every timestamp in the app was
		// off. Setting it once, here, before anything else runs, fixes it
		// everywhere at once rather than threading ZoneId through every call site.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(HeailBackendApplication.class, args);
	}
}
