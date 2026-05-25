package com.plagod.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AvatarResourceConfig implements WebMvcConfigurer {

    @Value("${wifi.upload.avatar-dir:uploads/avatars}")
    private String avatarDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(avatarDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/users/avatars/**")
                .addResourceLocations(root.toUri().toASCIIString());
    }
}
