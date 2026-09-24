package com.dispel4py.rest.trace;

import com.dispel4py.rest.model.Execution;
import com.dispel4py.rest.service.ExecutionServiceImpl;
import com.dispel4py.rest.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrdinaryExecutionTest {
    @Test void controllerSubscriptionDispatchesExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        WebClient transport = WebClient.builder().exchangeFunction(req -> Mono.defer(() -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"result\":[]}\n").build());
        })).build();
        ExecutionServiceImpl service = new ExecutionServiceImpl(mock(WorkflowService.class)) {
            @Override protected WebClient executionClient(String url) { return transport; }
        };
        ReflectionTestUtils.setField(service, "env", new MockEnvironment()
            .withProperty("laminar.execution.url", "http://engine"));
        var flux = service.runWorkflow(new Execution(), "rosa");
        assertEquals(0, calls.get(), "Preparing a request must not submit it");
        assertFalse(flux.collectList().block().isEmpty());
        assertEquals(1, calls.get(), "One client request must launch one execution");
    }

    @Test void processCountSurvivesServerPayloadRoundTrip() throws Exception {
        ObjectMapper json = new ObjectMapper();
        Execution request = json.readValue("{\"workflowId\":47,\"process\":2,\"numProcesses\":3}", Execution.class);
        assertEquals(3, request.getNumProcesses());
        assertEquals(3, json.valueToTree(request).path("numProcesses").asInt());
    }
}
