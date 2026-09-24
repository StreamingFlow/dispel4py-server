-- Additive, idempotent MySQL/MariaDB migration. Existing registry tables stay intact.
CREATE TABLE IF NOT EXISTS trace_profile (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  workflow_id INT NOT NULL,
  mapping VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  num_processes INT NOT NULL,
  engine_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  run_id VARCHAR(64), submitted_by VARCHAR(255),
  started_at VARCHAR(40), completed_at VARCHAR(40), wall_seconds DOUBLE,
  metadata_json LONGTEXT,
  lease_token VARCHAR(64), lease_until_ms BIGINT, last_error TEXT,
  UNIQUE KEY trace_configuration (workflow_id, mapping, num_processes, engine_id),
  CONSTRAINT trace_workflow_fk FOREIGN KEY (workflow_id) REFERENCES workflows(workflow_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS trace_pe (
  profile_id BIGINT NOT NULL, pe_id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  total_count BIGINT, total_seconds DOUBLE, cpu_seconds DOUBLE, rss_max_bytes BIGINT,
  metrics_json LONGTEXT NOT NULL,
  PRIMARY KEY (profile_id, pe_id),
  CONSTRAINT trace_pe_profile_fk FOREIGN KEY (profile_id) REFERENCES trace_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS trace_instance (
  profile_id BIGINT NOT NULL, instance_id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, pe_id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  rank_label VARCHAR(64), total_count BIGINT, total_seconds DOUBLE, cpu_seconds DOUBLE,
  rss_max_bytes BIGINT, metrics_json LONGTEXT NOT NULL,
  PRIMARY KEY (profile_id, instance_id), KEY trace_instance_pe (profile_id, pe_id),
  CONSTRAINT trace_instance_profile_fk FOREIGN KEY (profile_id) REFERENCES trace_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS trace_iteration (
  profile_id BIGINT NOT NULL, iteration_row BIGINT NOT NULL,
  pe_id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin, instance_id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin, iteration_index BIGINT,
  elapsed_seconds DOUBLE, cpu_seconds DOUBLE, rss_peak_bytes BIGINT,
  metrics_json LONGTEXT NOT NULL,
  PRIMARY KEY (profile_id, iteration_row), KEY trace_iteration_instance (profile_id, instance_id),
  CONSTRAINT trace_iteration_profile_fk FOREIGN KEY (profile_id) REFERENCES trace_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS trace_artifact (
  profile_id BIGINT NOT NULL PRIMARY KEY,
  content_zip LONGBLOB NOT NULL,
  CONSTRAINT trace_artifact_profile_fk FOREIGN KEY (profile_id) REFERENCES trace_profile(id) ON DELETE CASCADE
) ENGINE=InnoDB;
