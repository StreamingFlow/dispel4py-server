package com.dispel4py.rest.service;

import com.dispel4py.rest.dao.WorkflowFtsDao;
import com.dispel4py.rest.model.WorkflowFts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WorkflowSearchServiceImpl implements WorkflowSearchService {

    private final WorkflowFtsDao workflowFtsDao;

    public WorkflowSearchServiceImpl(WorkflowFtsDao workflowFtsDao) {
        this.workflowFtsDao = workflowFtsDao;
    }

    @Override
    public void index(WorkflowFts ftsData) {
        workflowFtsDao.insertManual(
                ftsData.getId(),
                ftsData.getName(),
                ftsData.getDescription(),
                ftsData.getTags(),
                ftsData.getKeywords()
        );
    }

    @Override
    public List<WorkflowFts> search(String query, int limit) {
        return workflowFtsDao.searchRanked(query, limit);
    }

    @Override
    public void delete(Long id) {
        workflowFtsDao.delete(id);
    }
}