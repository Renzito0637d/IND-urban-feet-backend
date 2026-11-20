package com.urbanfeet_backend.Config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DotenvApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();
        
        // Priorizar ./IND-urban-feet-backend/.env si se arranca desde la raíz del workspace
        java.io.File backendEnv = new java.io.File("./IND-urban-feet-backend/.env");
        Dotenv dotenv;
        if (backendEnv.exists()) {
            dotenv = Dotenv.configure()
                    .directory("./IND-urban-feet-backend")
                    .ignoreIfMissing()
                    .load();
        } else {
            dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
        }

        Map<String, Object> envMap = new HashMap<>();
        dotenv.entries().forEach(entry -> {
            envMap.put(entry.getKey(), entry.getValue());
            if (System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        if (!envMap.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", envMap));
        }
    }
}
