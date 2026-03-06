package com.dispel4py.rest.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.math.BigInteger;

@Component
public class PEFtsInitializer {

    private final EntityManager entityManager;

    public PEFtsInitializer(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        // Create Table if not exists
        entityManager.createNativeQuery(
                "CREATE TABLE IF NOT EXISTS pes_fts (" +
                        "id BIGINT PRIMARY KEY, name TEXT, description TEXT, tags TEXT, keywords TEXT) ENGINE=InnoDB"
        ).executeUpdate();

        // Check if the index exists to avoid 'Duplicate key' error
        String checkIndexSql = "SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE table_schema = DATABASE() " +
                "AND table_name = 'pes_fts' " +
                "AND index_name = 'pes_fts_idx'";

        BigInteger count = (BigInteger) entityManager.createNativeQuery(checkIndexSql).getSingleResult();

        // Only create if it doesn't exist
        if (count.intValue() == 0) {
            try {
                entityManager.createNativeQuery(
                        "ALTER TABLE pes_fts ADD FULLTEXT INDEX pes_fts_idx (name, description, tags, keywords)"
                ).executeUpdate();
            } catch (Exception e) {
                // Final fallback in case of race conditions
                System.out.println("Note: Index 'pes_fts_idx' already exists or could not be created.");
            }
        }
    }
}