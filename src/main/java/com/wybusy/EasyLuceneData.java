package com.wybusy;

public class EasyLuceneData {
    private String id;
    private String content;
    private String json;
    private String highLightContent;
    private Float score;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public String getHighLightContent() {
        return highLightContent;
    }

    public void setHighLightContent(String highLightContent) {
        this.highLightContent = highLightContent;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }
}