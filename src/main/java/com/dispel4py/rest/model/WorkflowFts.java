package com.dispel4py.rest.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "workflows_fts")
public class WorkflowFts {
    @Id
    @Column(name = "id")
    private Long id;

    // We use columnDefinition to ensure these are types MySQL can index
    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Transient
    private Double score;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}