package com.dispel4py.rest.service;

import com.dispel4py.rest.dao.PEFtsDao;
import com.dispel4py.rest.model.PEFts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PESearchServiceImpl implements PESearchService {

    private final PEFtsDao peFtsDao;

    public PESearchServiceImpl(PEFtsDao peFtsDao) {
        this.peFtsDao = peFtsDao;
    }

    @Override
    public PEFts index(PEFts data) {
        peFtsDao.insertManual(data.getId(), data.getName(), data.getDescription(), data.getTags(), data.getKeywords());
        return data;
    }

    @Override
    public List<PEFts> search(String q, int limit) {
        return peFtsDao.searchRanked(q, limit);
    }

    @Override
    public void delete(Long id) {
        peFtsDao.delete(id);
    }
}