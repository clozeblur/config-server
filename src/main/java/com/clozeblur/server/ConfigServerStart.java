package com.clozeblur.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by clozeblur
 * on 2018/1/1
 */
@EnableConfigServer
@EnableEurekaClient
@EnableScheduling
@EnableCaching
@SpringBootApplication
@ComponentScan(basePackages = {"com.clozeblur.server"})
public class ConfigServerStart {

    private static final Logger logger = LoggerFactory.getLogger(ConfigServerStart.class);
    private static final String TMP_DIR = "/home/log/config-server/config-repo";

    public static void main(String[] args) {
        // 由于linux会自动删除默认临时目录中的文件，这里手动指定临时文件目录
        System.setProperty("java.io.tmpdir", TMP_DIR);
        File tmpDir = new File(TMP_DIR);
        if (!tmpDir.exists()) {
            if (tmpDir.mkdirs()) {
                logger.info("创建临时目录:{}", tmpDir.getPath());
            } else {
                logger.error("创建临时失败");
            }
        }
        logger.info("Change tmp dir to: {}", System.getProperty("java.io.tmpdir"));
        List<String> list = new ArrayList<>();
        list.addAll(Arrays.asList(args));

        SpringApplication.run(ConfigServerStart.class, list.toArray(new String[]{})).getEnvironment();
    }
}
