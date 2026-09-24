package com.dispel4py.rest.trace;

import com.dispel4py.rest.model.Workflow;
import com.dispel4py.rest.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class TraceControllerTest {
    WorkflowService workflows=mock(WorkflowService.class);
    TraceRepository traces=mock(TraceRepository.class);
    TraceEngineGateway engine=mock(TraceEngineGateway.class);
    ObjectMapper json=new ObjectMapper();
    MockEnvironment env=new MockEnvironment().withProperty("laminar.execution.url","http://engine-one");
    TraceController controller;
    @BeforeEach void setup() {
        Workflow wf=mock(Workflow.class);
        when(wf.getWorkflowId()).thenReturn(42);
        when(wf.getWorkflowCode()).thenReturn("workflow");
        when(workflows.getWorkflowByID(42L,"rosa")).thenReturn(wf);
        when(workflows.getWorkflowByName("sensor","rosa")).thenReturn(wf);
        when(traces.begin(eq(42),anyString(),anyInt(),anyString(),anyString(),anyInt())).thenReturn(7L);
        controller=new TraceController(workflows,traces,json,env,engine);
    }
    TraceRequest request() {
        TraceRequest r=new TraceRequest();r.workflowName="sensor";r.mapping="multi";
        r.numProcesses=4;r.inputCode="input";return r;
    }
    JsonNode result(String engineId) throws Exception {
        return json.readTree("{\"schemaVersion\":1,\"workflowId\":42,\"mapping\":\"multi\",\"numProcesses\":4,\"engineId\":\""+engineId+"\",\"perPe\":[],\"instances\":[],\"iterations\":[],\"artifactsBase64\":\"\"}");
    }
    @Test void resolvesNameAndDispatchesExactlyOnce() throws Exception {
        when(engine.execute(eq("http://engine-one"),any(),anyInt())).thenReturn(result("default"));
        controller.run("rosa",request());
        verify(engine,times(1)).execute(eq("http://engine-one"),argThat(p->p.path("workflowId").asInt()==42 && p.path("numProcesses").asInt()==4),eq(3600));
        verify(traces).complete(eq(7L),anyString(),eq("rosa"),any());
        verify(traces,never()).fail(anyLong(),anyString(),any());
    }
    @Test void routesOnlyToConfiguredEngine() throws Exception {
        env.withProperty("laminar.trace.engines","{\"default\":\"http://engine-one\",\"hpc\":\"http://engine-two\"}");
        TraceRequest r=request();r.engineId="hpc";
        when(engine.execute(eq("http://engine-two"),any(),anyInt())).thenReturn(result("hpc"));
        controller.run("rosa",r);
        verify(engine).execute(eq("http://engine-two"),any(),anyInt());
        r.engineId="http://arbitrary-host";
        assertThrows(ResponseStatusException.class,()->controller.run("rosa",r));
        verify(engine,times(1)).execute(anyString(),any(),anyInt());
    }
    @Test void mismatchedEngineResponseCannotBePersisted() throws Exception {
        when(engine.execute(anyString(),any(),anyInt())).thenReturn(result("wrong"));
        assertThrows(ResponseStatusException.class,()->controller.run("rosa",request()));
        verify(traces,never()).complete(anyLong(),anyString(),anyString(),any());
        verify(traces).fail(eq(7L),anyString(),contains("mismatched"));
    }
    @Test void userMustOwnWorkflowToReadArtifacts() {
        when(traces.workflow(7L)).thenReturn(42);
        when(workflows.getWorkflowByID(42L,"other")).thenThrow(new RuntimeException("unauthorized"));
        assertThrows(RuntimeException.class,()->controller.artifacts("other",7L,null));
        verify(traces,never()).artifacts(anyLong(),any());
    }
    @Test void invalidProcessCountIsRejectedBeforeExecution() {
        TraceRequest r=request();r.mapping="simple";
        assertThrows(ResponseStatusException.class,()->controller.run("rosa",r));
        verifyNoInteractions(engine);
        verify(traces,never()).begin(anyInt(),anyString(),anyInt(),anyString(),anyString(),anyInt());
    }
    @Test void iterationPaginationIsBounded() {
        when(traces.workflow(7L)).thenReturn(42);
        assertThrows(ResponseStatusException.class,()->controller.iterations("rosa",7L,0,10001,null));
        verify(traces,never()).iterations(anyLong(),anyInt(),anyInt(),any());
    }
}
