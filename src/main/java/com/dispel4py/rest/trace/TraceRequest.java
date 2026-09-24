package com.dispel4py.rest.trace;

import java.util.List;
import java.util.Map;

public class TraceRequest {
    public Long workflowId;
    public String workflowName;
    public String mapping;
    public Integer numProcesses;
    public String engineId = "default";
    public String inputCode;
    public boolean inputsByPe = false;
    public double memorySamplingInterval = 0.01;
    public int timeoutSeconds = 3600;
    public boolean graphFigures = false;
    public List<Map<String, String>> resourceFiles = List.of();
}
