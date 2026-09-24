package com.dispel4py.rest.trace;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class TraceEngineGateway {
    public JsonNode execute(String url, JsonNode payload, int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout((timeoutSeconds + 60) * 1000);
        return new RestTemplate(factory).postForObject(
                url.replaceAll("/+$", "") + "/trace_run", payload, JsonNode.class);
    }
}
