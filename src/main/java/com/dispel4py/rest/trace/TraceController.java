package com.dispel4py.rest.trace;

import com.dispel4py.rest.model.Workflow;
import com.dispel4py.rest.service.WorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/execution/{user}")
public class TraceController {
    private final WorkflowService workflows;
    private final TraceRepository traces;
    private final ObjectMapper json;
    private final Environment env;
    private final TraceEngineGateway engine;
    public TraceController(WorkflowService workflows, TraceRepository traces, ObjectMapper json, Environment env, TraceEngineGateway engine) {
        this.workflows=workflows; this.traces=traces; this.json=json; this.env=env; this.engine=engine;
    }

    private Workflow resolve(Long id, String name, String user) {
        if ((id == null) == (name == null || name.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Specify exactly one workflowId or workflowName");
        return id != null ? workflows.getWorkflowByID(id,user) : workflows.getWorkflowByName(name,user);
    }
    private JsonNode engines() {
        try {
            String configured = env.getProperty("laminar.trace.engines");
            if (configured != null) return json.readTree(configured);
            return json.createObjectNode().put("default", env.getRequiredProperty("laminar.execution.url"));
        } catch (Exception e) { throw new IllegalStateException("Invalid laminar.trace.engines configuration",e); }
    }

    @PostMapping("/trace_run")
    public JsonNode run(@PathVariable String user, @RequestBody TraceRequest request) {
        Workflow wf = resolve(request.workflowId, request.workflowName, user);
        if (request.mapping == null || !Set.of("simple","multi","mpi").contains(request.mapping))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"mapping must be simple, multi or mpi");
        if (request.numProcesses == null && request.mapping.equals("simple")) request.numProcesses=1;
        if (request.numProcesses == null || request.numProcesses < 1 || request.numProcesses > 256 ||
                (request.mapping.equals("simple") && request.numProcesses != 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid numProcesses (simple requires 1; multi/mpi require an explicit count)");
        if (request.timeoutSeconds < 1 || request.timeoutSeconds > 86400 ||
                !Double.isFinite(request.memorySamplingInterval) || request.memorySamplingInterval < 0 || request.memorySamplingInterval > 60 ||
                request.inputCode == null || request.inputCode.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid timeout, sampling interval or inputCode");
        if (request.engineId == null || !request.engineId.matches("[a-zA-Z0-9_-]{1,64}") || !engines().has(request.engineId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown engine ID");
        String url = engines().path(request.engineId).asText();
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            throw new IllegalStateException("Engine URL must use http(s)");
        String token = UUID.randomUUID().toString();
        long id = traces.begin(wf.getWorkflowId(),request.mapping,request.numProcesses,request.engineId,token,request.timeoutSeconds);
        try {
            ObjectNode payload = json.valueToTree(request);
            payload.put("workflowId",wf.getWorkflowId()).put("workflowCode",wf.getWorkflowCode())
                   .put("moduleSourceCode",wf.getModuleSourceCode()).put("moduleName",wf.getModuleName());
            // Exactly one dispatch; no duplicate HTTP subscription.
            JsonNode result = engine.execute(url,payload,request.timeoutSeconds);
            if (result == null || result.path("schemaVersion").asInt() != 1 ||
                    result.path("workflowId").asInt() != wf.getWorkflowId() ||
                    !result.path("engineId").asText().equals(request.engineId) ||
                    !result.path("mapping").asText().equals(request.mapping) ||
                    result.path("numProcesses").asInt() != request.numProcesses ||
                    !result.path("perPe").isArray() || !result.path("instances").isArray() ||
                    !result.path("iterations").isArray() || !result.path("artifactsBase64").isTextual())
                throw new IllegalStateException("Engine returned an invalid or mismatched trace");
            traces.complete(id,token,user,result);
            return traces.detail(id);
        } catch (Exception error) {
            String message = error instanceof RestClientResponseException ?
                    ((RestClientResponseException)error).getResponseBodyAsString() : error.getMessage();
            traces.fail(id,token,message);
            if (error instanceof ResponseStatusException) throw (ResponseStatusException)error;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Trace failed; previous completed results retained. " + message,error);
        }
    }

    @GetMapping("/traces")
    public List<JsonNode> list(@PathVariable String user, @RequestParam(required=false) Long workflowId,
            @RequestParam(required=false) String workflowName, @RequestParam(required=false) String mapping,
            @RequestParam(required=false) Integer numProcesses, @RequestParam(required=false) String engineId) {
        Workflow wf = resolve(workflowId,workflowName,user);
        List<JsonNode> out = new ArrayList<>();
        for (long id : traces.find(wf.getWorkflowId(),mapping,numProcesses,engineId)) out.add(traces.detail(id));
        return out;
    }
    private void authorize(long id, String user) { workflows.getWorkflowByID((long)traces.workflow(id),user); }
    @GetMapping("/traces/{id}")
    public JsonNode get(@PathVariable String user,@PathVariable long id) { authorize(id,user); return traces.detail(id); }
    @GetMapping("/traces/{id}/artifacts")
    public ResponseEntity<byte[]> artifacts(@PathVariable String user,@PathVariable long id,
            @RequestParam(required=false) String runId) {
        authorize(id,user);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=trace-"+id+".zip").body(traces.artifacts(id,runId));
    }
    @GetMapping("/traces/{id}/iterations")
    public List<JsonNode> iterations(@PathVariable String user,@PathVariable long id,
            @RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="1000") int limit,
            @RequestParam(required=false) String runId) {
        authorize(id,user);
        if (offset < 0 || limit < 1 || limit > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        return traces.iterations(id,offset,limit,runId);
    }
}
