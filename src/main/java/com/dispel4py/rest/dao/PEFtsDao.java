package com.dispel4py.rest.dao;

import com.dispel4py.rest.model.PEFts;

import java.util.List;

public interface PEFtsDao {
    void insertManual(Long id, String name, String desc, String tags, String keywords);

    List<PEFts> searchRanked(String searchTerm, int limit);

    void delete(Long id);
}