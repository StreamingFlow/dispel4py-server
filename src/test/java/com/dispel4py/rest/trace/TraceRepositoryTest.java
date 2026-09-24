package com.dispel4py.rest.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

class TraceRepositoryTest {
    AnnotationConfigApplicationContext context;
    TraceRepository repository;
    JdbcTemplate db;
    ObjectMapper json = new ObjectMapper();
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return source;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean("traceSchema") Object schema(JdbcTemplate db) throws Exception {
            db.execute("CREATE TABLE workflows(workflow_id INT PRIMARY KEY)");
            db.update("INSERT INTO workflows VALUES (1)");
            String ddl = new String(new ClassPathResource("db/trace_schema.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8)
                .replace(" CHARACTER SET ascii COLLATE ascii_bin","").replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin","").replace(" ENGINE=InnoDB","");
            for (String statement : ddl.split(";")) if (!statement.isBlank()) db.execute(statement);
            return new Object();
        }
        @Bean TraceRepository repository(JdbcTemplate db, ObjectMapper json) { return new TraceRepository(db,json); }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        repository=context.getBean(TraceRepository.class); db=context.getBean(JdbcTemplate.class);
    }
    @AfterEach void close() { context.close(); }
    JsonNode trace(String run) throws Exception {
        ObjectNode trace=(ObjectNode)json.readTree("{\"runId\":\"x\",\"startedAt\":\"start\",\"completedAt\":\"end\",\"wallSeconds\":1.0,\"metadata\":{},\"artifactsBase64\":\"eA==\",\"perPe\":[{\"pe_id\":\"PE\",\"total_count\":1,\"total_secs\":1.0,\"total_cpu_secs\":0.2,\"rss_max_bytes\":2000}],\"instances\":[{\"pe_id\":\"PE\",\"instance_id\":\"PE@0\",\"rank\":\"0\",\"total_count\":1}],\"iterations\":[{\"pe_id\":\"PE\",\"instance_id\":\"PE@0\",\"iteration_index\":1,\"iteration_secs\":1.0}]}");
        trace.put("runId",run);return trace;
    }
    @Test void rerunReplacesChildrenAndKeepsStableProfile() throws Exception {
        long id=repository.begin(1,"simple",1,"default","a",60);
        repository.complete(id,"a","rosa",trace("first"));
        assertEquals(id,repository.begin(1,"simple",1,"default","b",60));
        repository.complete(id,"b","rosa",trace("second"));
        assertEquals("second",repository.detail(id).path("runId").asText());
        for (String table:java.util.List.of("trace_profile","trace_pe","trace_instance","trace_iteration","trace_artifact"))
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
    }
    @Test void eachConfigurationIsIndependent() throws Exception {
        String[][] configs={{"simple","1","default"},{"multi","4","default"},{"mpi","4","default"},{"multi","8","default"},{"multi","4","hpc"}};
        for (String[] c:configs) { long id=repository.begin(1,c[0],Integer.parseInt(c[1]),c[2],"a",60);repository.complete(id,"a","rosa",trace("r")); }
        assertEquals(5,repository.find(1,null,null,null).size());
        assertEquals(1,repository.find(1,"multi",4,"hpc").size());
    }
    @Test void failedRerunRetainsLastCompletedTrace() throws Exception {
        long id=repository.begin(1,"simple",1,"default","a",60);repository.complete(id,"a","rosa",trace("first"));
        repository.begin(1,"simple",1,"default","b",60);repository.fail(id,"b","worker failed");
        assertEquals("first",repository.detail(id).path("runId").asText());
        assertEquals("worker failed",repository.detail(id).path("lastError").asText());
        assertEquals(1,repository.iterations(id,0,100,null).size());
    }
    @Test void failedTransactionRollsBackDeletedChildren() throws Exception {
        long id=repository.begin(1,"simple",1,"default","a",60);repository.complete(id,"a","rosa",trace("first"));
        repository.begin(1,"simple",1,"default","b",60);
        JsonNode bad=trace("bad");((com.fasterxml.jackson.databind.node.ArrayNode)bad.path("perPe")).add(bad.path("perPe").get(0));
        assertThrows(Exception.class,()->repository.complete(id,"b","rosa",bad));
        repository.fail(id,"b","insert failed");
        assertEquals("first",repository.detail(id).path("runId").asText());
        assertEquals(1,repository.detail(id).path("perPe").size());
        assertArrayEquals(new byte[]{'x'},repository.artifacts(id,null));
    }
    @Test void concurrentOrStaleWritersCannotReplaceCurrentProfile() throws Exception {
        long id=repository.begin(1,"simple",1,"default","a",60);
        assertThrows(ResponseStatusException.class,()->repository.begin(1,"simple",1,"default","b",60));
        db.update("UPDATE trace_profile SET lease_until_ms=0 WHERE id=?",id);
        repository.begin(1,"simple",1,"default","b",60);
        assertThrows(ResponseStatusException.class,()->repository.complete(id,"a","rosa",trace("stale")));
        repository.complete(id,"b","rosa",trace("new"));
        assertEquals("new",repository.detail(id).path("runId").asText());
    }
    @Test void deletingWorkflowCascadesToAllTraceData() throws Exception {
        long id=repository.begin(1,"simple",1,"default","a",60);repository.complete(id,"a","rosa",trace("first"));
        db.update("DELETE FROM workflows WHERE workflow_id=1");
        for (String table:java.util.List.of("trace_profile","trace_pe","trace_instance","trace_iteration","trace_artifact"))
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
    }
    @Test void staleReportCannotDownloadNewRunsArtifacts() throws Exception {
        long id=repository.begin(1,"simple",1,"default","a",60);repository.complete(id,"a","rosa",trace("first"));
        assertArrayEquals(new byte[]{'x'},repository.artifacts(id,"first"));
        repository.begin(1,"simple",1,"default","b",60);repository.complete(id,"b","rosa",trace("second"));
        assertThrows(ResponseStatusException.class,()->repository.artifacts(id,"first"));
        assertThrows(ResponseStatusException.class,()->repository.iterations(id,0,100,"first"));
    }
}
