package com.dispel4py.rest.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

@Repository
@DependsOn("traceSchema")
public class TraceRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public TraceRepository(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    @Transactional
    public long begin(int workflow, String mapping, int processes, String engine, String token, int timeout) {
        db.update("INSERT INTO trace_profile(workflow_id,mapping,num_processes,engine_id) VALUES (?,?,?,?) " +
                  "ON DUPLICATE KEY UPDATE id=id", workflow, mapping, processes, engine);
        Map<String,Object> row = db.queryForMap("SELECT id,lease_token,lease_until_ms FROM trace_profile " +
                "WHERE workflow_id=? AND mapping=? AND num_processes=? AND engine_id=? FOR UPDATE",
                workflow, mapping, processes, engine);
        if (row.get("lease_token") != null && ((Number)row.get("lease_until_ms")).longValue() > System.currentTimeMillis())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This trace configuration is already running");
        long id = ((Number)row.get("id")).longValue();
        db.update("UPDATE trace_profile SET lease_token=?,lease_until_ms=?,last_error=NULL WHERE id=?",
                token, System.currentTimeMillis() + (timeout + 120L) * 1000, id);
        return id;
    }

    @Transactional
    public void complete(long id, String token, String user, JsonNode trace) {
        Map<String,Object> locked = db.queryForMap("SELECT lease_token FROM trace_profile WHERE id=? FOR UPDATE", id);
        if (!token.equals(locked.get("lease_token")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A newer execution owns this configuration");
        for (String table : List.of("trace_pe", "trace_instance", "trace_iteration", "trace_artifact"))
            db.update("DELETE FROM " + table + " WHERE profile_id=?", id);
        insertMetrics(id, "trace_pe", trace.path("perPe"));
        insertMetrics(id, "trace_instance", trace.path("instances"));
        List<Object[]> batch = new ArrayList<>();
        long index = 0;
        for (JsonNode row : trace.path("iterations")) {
            batch.add(new Object[]{id, ++index, text(row,"pe_id"), text(row,"instance_id"), number(row,"iteration_index"),
                    number(row,"iteration_secs"), number(row,"cpu_secs"), number(row,"rss_peak_observed_bytes"), row.toString()});
            if (batch.size() == 500) { insertIterations(batch); batch.clear(); }
        }
        if (!batch.isEmpty()) insertIterations(batch);
        db.update("INSERT INTO trace_artifact(profile_id,content_zip) VALUES (?,?)", id,
                Base64.getDecoder().decode(trace.path("artifactsBase64").asText()));
        db.update("UPDATE trace_profile SET run_id=?,submitted_by=?,started_at=?,completed_at=?,wall_seconds=?," +
                  "metadata_json=?,lease_token=NULL,lease_until_ms=NULL,last_error=NULL WHERE id=?",
                trace.path("runId").asText(), user, trace.path("startedAt").asText(), trace.path("completedAt").asText(),
                trace.path("wallSeconds").asDouble(), trace.path("metadata").toString(), id);
    }

    private void insertIterations(List<Object[]> rows) {
        db.batchUpdate("INSERT INTO trace_iteration(profile_id,iteration_row,pe_id,instance_id,iteration_index," +
                "elapsed_seconds,cpu_seconds,rss_peak_bytes,metrics_json) VALUES (?,?,?,?,?,?,?,?,?)", rows);
    }

    private void insertMetrics(long id, String table, JsonNode rows) {
        List<Object[]> values = new ArrayList<>();
        for (JsonNode row : rows) {
            if (table.equals("trace_pe")) values.add(new Object[]{id, text(row,"pe_id"), number(row,"total_count"),
                    number(row,"total_secs"), number(row,"total_cpu_secs"), number(row,"rss_max_bytes"), row.toString()});
            else values.add(new Object[]{id, text(row,"instance_id"), text(row,"pe_id"), text(row,"rank"),
                    number(row,"total_count"), number(row,"total_secs"), number(row,"total_cpu_secs"),
                    number(row,"rss_max_bytes"), row.toString()});
        }
        if (values.isEmpty()) return;
        if (table.equals("trace_pe")) db.batchUpdate("INSERT INTO trace_pe(profile_id,pe_id,total_count,total_seconds," +
                "cpu_seconds,rss_max_bytes,metrics_json) VALUES (?,?,?,?,?,?,?)", values);
        else db.batchUpdate("INSERT INTO trace_instance(profile_id,instance_id,pe_id,rank_label,total_count,total_seconds," +
                "cpu_seconds,rss_max_bytes,metrics_json) VALUES (?,?,?,?,?,?,?,?,?)", values);
    }

    public void fail(long id, String token, String message) {
        db.update("UPDATE trace_profile SET lease_token=NULL,lease_until_ms=NULL,last_error=? WHERE id=? AND lease_token=?",
                message == null ? "Trace failed" : message.substring(0, Math.min(message.length(), 4000)), id, token);
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public JsonNode detail(long id) {
        List<Map<String,Object>> rows = db.queryForList("SELECT * FROM trace_profile WHERE id=? AND completed_at IS NOT NULL", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No completed trace for this configuration");
        Map<String,Object> row = rows.get(0);
        ObjectNode out = json.createObjectNode();
        out.put("profileId", id).put("workflowId", ((Number)row.get("workflow_id")).intValue())
           .put("mapping", (String)row.get("mapping")).put("numProcesses", ((Number)row.get("num_processes")).intValue())
           .put("engineId", (String)row.get("engine_id")).put("runId", (String)row.get("run_id"))
           .put("submittedBy", (String)row.get("submitted_by")).put("startedAt", (String)row.get("started_at"))
           .put("completedAt", (String)row.get("completed_at")).put("wallSeconds", ((Number)row.get("wall_seconds")).doubleValue())
           .put("inProgress", row.get("lease_token") != null && ((Number)row.get("lease_until_ms")).longValue() > System.currentTimeMillis())
           .put("lastError", (String)row.get("last_error"));
        out.set("metadata", parse((String)row.get("metadata_json")));
        for (String[] pair : new String[][]{{"perPe","trace_pe"},{"instances","trace_instance"}}) {
            var array = out.putArray(pair[0]);
            for (String metric : db.queryForList("SELECT metrics_json FROM " + pair[1] + " WHERE profile_id=? ORDER BY pe_id", String.class, id))
                array.add(parse(metric));
        }
        return out;
    }

    public List<Long> find(int workflow, String mapping, Integer processes, String engine) {
        String sql = "SELECT id FROM trace_profile WHERE workflow_id=? AND completed_at IS NOT NULL";
        List<Object> args = new ArrayList<>(List.of(workflow));
        if (mapping != null) { sql += " AND mapping=?"; args.add(mapping); }
        if (processes != null) { sql += " AND num_processes=?"; args.add(processes); }
        if (engine != null) { sql += " AND engine_id=?"; args.add(engine); }
        return db.queryForList(sql + " ORDER BY mapping,num_processes,engine_id", Long.class, args.toArray());
    }

    public int workflow(long id) {
        List<Integer> rows = db.queryForList("SELECT workflow_id FROM trace_profile WHERE id=?", Integer.class, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found");
        return rows.get(0);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public byte[] artifacts(long id, String expectedRun) {
        guardRun(id, expectedRun);
        List<byte[]> rows = db.queryForList("SELECT content_zip FROM trace_artifact WHERE profile_id=?", byte[].class, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifacts not found");
        return rows.get(0);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<JsonNode> iterations(long id, int offset, int limit, String expectedRun) {
        guardRun(id, expectedRun);
        return db.query("SELECT metrics_json FROM trace_iteration WHERE profile_id=? ORDER BY iteration_row LIMIT ? OFFSET ?",
                (rs,n) -> parse(rs.getString(1)), id, limit, offset);
    }
    private void guardRun(long id, String expectedRun) {
        if (expectedRun == null) return;
        List<String> runs = db.queryForList("SELECT run_id FROM trace_profile WHERE id=?",String.class,id);
        if (runs.isEmpty() || !expectedRun.equals(runs.get(0)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Trace was replaced; fetch the current report before downloading or paging");
    }
    private JsonNode parse(String text) {
        try { return json.readTree(text); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored trace JSON", e); }
    }
    private static String text(JsonNode row, String key) { return row.path(key).isNull() ? null : row.path(key).asText(null); }
    private static Number number(JsonNode row, String key) { return row.path(key).isNumber() ? row.path(key).numberValue() : null; }
}
