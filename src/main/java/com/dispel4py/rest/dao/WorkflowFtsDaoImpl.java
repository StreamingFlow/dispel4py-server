package com.dispel4py.rest.dao;

import com.dispel4py.rest.model.WorkflowFts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;

@Repository
@Transactional // Ensures the EntityManager has an active session
public class WorkflowFtsDaoImpl implements WorkflowFtsDao {

    private final EntityManager entityManager;

    @Autowired
    public WorkflowFtsDaoImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void insertManual(Long wid, String name, String description, String tags, String keywords) {
        // Matches your exact cur.execute query using the project's EntityManager style
        Query query = entityManager.createNativeQuery(
                "INSERT INTO workflows_fts(id, name, description, tags, keywords) VALUES(?,?,?,?,?)"
        );

        query.setParameter(1, wid);
        query.setParameter(2, name);
        query.setParameter(3, description);
        query.setParameter(4, tags);
        query.setParameter(5, keywords);

        query.executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<WorkflowFts> searchRanked(String searchTerm, int limit) {
        // This order must be identical to the index created in FtsInitializer
        String sql = "SELECT id, name, description, tags, keywords, " +
                "MATCH(name, description, tags, keywords) AGAINST(:search IN NATURAL LANGUAGE MODE) AS score " +
                "FROM workflows_fts " +
                "WHERE MATCH(name, description, tags, keywords) AGAINST(:search IN NATURAL LANGUAGE MODE) " +
                "ORDER BY score DESC LIMIT :limit";

        return entityManager.createNativeQuery(sql, WorkflowFts.class)
                .setParameter("search", searchTerm)
                .setParameter("limit", limit)
                .getResultList();
    }

    @Override
    public void delete(Long id) {
        entityManager.createNativeQuery("DELETE FROM workflows_fts WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}