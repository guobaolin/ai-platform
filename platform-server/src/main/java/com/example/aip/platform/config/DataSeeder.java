package com.example.aip.platform.config;

import com.example.aip.platform.domain.AiApp;
import com.example.aip.platform.domain.Dataset;
import com.example.aip.platform.repo.AiAppRepository;
import com.example.aip.platform.repo.DatasetRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    public ApplicationRunner seedDemoData(AiAppRepository appRepo, DatasetRepository dsRepo) {
        return args -> {
            if (appRepo.findById("demo-app").isEmpty()) {
                AiApp app = new AiApp();
                app.setAppId("demo-app");
                app.setName("Demo Agent");
                app.setSystemPrompt("你是一个有帮助的中文助手。");
                app.setModelProvider("mock");
                app.setModel("mock-gpt");
                app.setTemperature(0.7);
                appRepo.save(app);
            }
            if (dsRepo.findById("demo-ds").isEmpty()) {
                Dataset ds = new Dataset();
                ds.setId("demo-ds");
                ds.setName("Demo 知识库");
                dsRepo.save(ds);
            }
            log.info("seeded demo-app / demo-ds");
        };
    }
}
