package com.dispel4py.rest.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.List;

/**
 * Model Class to represent Processing Elements (PEs)
 */
@Entity
@Table(name = "processing_elements")
public class PE extends Registry {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column
    Integer peId;
    @Column(unique = true)
    String peName;
    @Column(length = 5000)
    String peCode;
    @Column(length = 5000)
    String sourceCode;
    @Column
    String description;
    @Column
    String peImports;
    @Lob
    @Column(length = 20000)
    String codeEmbedding;
    @Lob
    @Column(length = 20000)
    String descEmbedding;
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    String astEmbedding;
    @JsonIgnore
    @ManyToMany(mappedBy = "PEs")
    private List<Workflow> workflows;

    @ManyToMany
    @JoinColumn(name = "userId", nullable = false)
    List<User> user;

    public PE(Integer id, String PEName, String PECode, String sourceCode, 
              String description, String peImports,
              String codeEmbeddings, String descEmbeddings, String astEmbedding, List<User> user) {

        this.peId = id;
        this.peName = PEName;
        this.peCode = PECode;
        this.sourceCode = sourceCode;
        this.description = description;
        this.peImports = peImports;
        this.user = user;
        this.codeEmbedding = codeEmbeddings;
        this.descEmbedding = descEmbeddings;
        this.astEmbedding = astEmbedding;

    }

    public PE() {

    }

    public Integer getPeId() {
        return peId;
    }

    public void setPeId(Integer peId) {
        this.peId = peId;
    }

    public String getPeName() {
        return peName;
    }

    public void setPeName(String PEName) {
        this.peName = PEName;
    }

    public String getpeCode() {
        return peCode;
    }

    public void setPeCode(String PECode) {
        this.peCode = PECode;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDescEmbedding(String descEmbedding) {
        this.descEmbedding = descEmbedding;
    }

    public List<Workflow> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<Workflow> workflows) {
        this.workflows = workflows;
    }

    public String getPeImports() {
        return peImports;
    }

    public void setPeImports(String peImports) {
        this.peImports = peImports;
    }

    public List<User> getUser() {
        return user;
    }

    public void setUser(List<User> user) {
        this.user = user;
    }

    public String getCodeEmbedding() {
        return codeEmbedding;
    }

    public void setCodeEmbedding(String codeEmbeddings) {
        this.codeEmbedding = codeEmbeddings;
    }

    public String getDescEmbedding() {
        return descEmbedding;
    }

    public String getAstEmbedding() {
        return astEmbedding;
    }

    public void setAstEmbedding(String astEmbedding) {
        this.astEmbedding = astEmbedding;
    }


    @Override
    public String toString() {
        return "PE(" + this.getPeId() + "\n" + this.getPeName()
                + ")";
    }


}

