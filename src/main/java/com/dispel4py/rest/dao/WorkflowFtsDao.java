package com.dispel4py.rest.dao;

import com.dispel4py.rest.model.WorkflowFts;

import java.util.List;

public interface WorkflowFtsDao {
    void insertManual(Long wid, String name, String description, String tags, String keywords);

    List<WorkflowFts> searchRanked(String searchTerm, int limit);

    void delete(Long id);
}