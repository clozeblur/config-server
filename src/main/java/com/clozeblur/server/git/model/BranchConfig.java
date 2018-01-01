package com.clozeblur.server.git.model;

import java.util.List;

/**
 * Created by clozeblur
 * on 2017/7/19
 */
public class BranchConfig {

    private List<Difference> differences;

    private String version;

    public List<Difference> getDifferences() {
        return differences;
    }

    public void setDifferences(List<Difference> differences) {
        this.differences = differences;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BranchConfig{" +
                "differences=" + differences +
                ", version='" + version + '\'' +
                '}';
    }
}
