package com.clozeblur.server.git.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by clozeblur
 * on 2017/6/26
 */
@Component
@ConfigurationProperties(prefix = "spring.cloud.config.server.git")
public class Repo {

    private Map<String, Map<String, String>> repos = new HashMap<>();

    public Map<String, Map<String, String>> getRepos() {
        return repos;
    }

    public void setRepos(Map<String, Map<String, String>> repos) {
        this.repos = repos;
    }
}
