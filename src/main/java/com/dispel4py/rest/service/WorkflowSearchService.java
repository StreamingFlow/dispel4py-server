package com.dispel4py.rest.service;

import com.dispel4py.rest.model.WorkflowFts;

import java.util.List;

public interface WorkflowSearchService {
    void index(WorkflowFts ftsData);

    List<WorkflowFts> search(String query, int limit);

    void delete(Long id);
}