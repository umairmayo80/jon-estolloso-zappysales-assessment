package com.profiledirectory;

import com.profiledirectory.config.AppSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppSecurityProperties.class)
public class ProfileDirectoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileDirectoryApplication.class, args);
    }
}
