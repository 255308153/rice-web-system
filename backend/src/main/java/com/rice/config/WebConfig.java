package com.rice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${upload.path:./uploads/images/}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadPath).toAbsolutePath().normalize().toString();
        if (!absolutePath.endsWith("/")) {
            absolutePath = absolutePath + "/";
        }
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
