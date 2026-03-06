package com.dispel4py.rest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.math.BigInteger;

@Component
public class WorkflowFtsInitializer {

    @Autowired
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        try {
            // Create table if not exists
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS workflows_fts (" +
                            "id BIGINT PRIMARY KEY, " +
                            "name TEXT, " +
                            "description TEXT, " +
                            "tags TEXT, " +
                            "keywords TEXT) ENGINE=InnoDB"
            ).executeUpdate();

            // Check if FULLTEXT index exists
            String checkIndexSql = "SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE table_schema = DATABASE() " +
                    "AND table_name = 'workflows_fts' " +
                    "AND index_name = 'fts_idx'";

            BigInteger count = (BigInteger) entityManager.createNativeQuery(checkIndexSql).getSingleResult();

            if (count.intValue() == 0) {
                // Create the composite FULLTEXT index
                entityManager.createNativeQuery(
                        "ALTER TABLE workflows_fts ADD FULLTEXT INDEX fts_idx " +
                                "(name, description, tags, keywords)"
                ).executeUpdate();
                System.out.println("FULLTEXT index created successfully.");
            }
        } catch (Exception e) {
            System.err.println("FTS Initialization failed: " + e.getMessage());
        }
    }
}