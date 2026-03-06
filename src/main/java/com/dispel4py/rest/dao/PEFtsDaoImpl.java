package com.dispel4py.rest.dao;

import com.dispel4py.rest.model.PEFts;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;

@Repository
public class PEFtsDaoImpl implements PEFtsDao {
    private final EntityManager entityManager;

    public PEFtsDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insertManual(Long id, String name, String desc, String tags, String keywords) {
        Query q = entityManager.createNativeQuery("INSERT INTO pes_fts(id, name, description, tags, keywords) VALUES(?,?,?,?,?)");
        q.setParameter(1, id).setParameter(2, name).setParameter(3, desc).setParameter(4, tags).setParameter(5, keywords);
        q.executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PEFts> searchRanked(String searchTerm, int limit) {
        String sql = "SELECT id, name, description, tags, keywords, MATCH(name, description, tags, keywords) AGAINST(:search IN NATURAL LANGUAGE MODE) AS score " +
                "FROM pes_fts WHERE MATCH(name, description, tags, keywords) AGAINST(:search) ORDER BY score DESC LIMIT :limit";
        return entityManager.createNativeQuery(sql, PEFts.class).setParameter("search", searchTerm).setParameter("limit", limit).getResultList();
    }

    @Override
    public void delete(Long id) {
        entityManager.createNativeQuery("DELETE FROM pes_fts WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}