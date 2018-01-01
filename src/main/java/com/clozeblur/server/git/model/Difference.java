package com.clozeblur.server.git.model;

/**
 * Created by clozeblur
 * on 2017/6/26
 */
public class Difference {

    // ====================================================================================================
    //                                            git版本差异
    // ====================================================================================================

    private String text;
    private Integer addSize;
    private Integer subSize;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getAddSize() {
        return addSize;
    }

    public void setAddSize(Integer addSize) {
        this.addSize = addSize;
    }

    public Integer getSubSize() {
        return subSize;
    }

    public void setSubSize(Integer subSize) {
        this.subSize = subSize;
    }

    @Override
    public String toString() {
        return "Difference{" +
                "text='" + text + '\'' +
                ", addSize=" + addSize +
                ", subSize=" + subSize +
                '}';
    }
}
