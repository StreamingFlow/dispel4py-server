package com.dispel4py.rest.service;

import com.dispel4py.rest.model.PEFts;

import java.util.List;

public interface PESearchService {
    PEFts index(PEFts data);

    List<PEFts> search(String q, int limit);

    void delete(Long id);
}