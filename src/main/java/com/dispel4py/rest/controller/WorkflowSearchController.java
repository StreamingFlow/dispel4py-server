package com.dispel4py.rest.controller;

import com.dispel4py.rest.model.WorkflowFts;
import com.dispel4py.rest.service.WorkflowSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/registry/{user}/workflow/search")
public class WorkflowSearchController {

    private final WorkflowSearchService workflowSearchService;

    public WorkflowSearchController(WorkflowSearchService workflowSearchService) {
        this.workflowSearchService = workflowSearchService;
    }

    @GetMapping
    public List<WorkflowFts> search(@PathVariable("user") String user,
                                    @RequestParam("q") String query,
                                    @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return workflowSearchService.search(query, limit);
    }

    @PostMapping
    public void index(@PathVariable("user") String user, @RequestBody WorkflowFts ftsData) {
        workflowSearchService.index(ftsData);
    }

    @DeleteMapping
    public void delete(@PathVariable("user") String user, @RequestParam("id") Long id) {
        workflowSearchService.delete(id);
    }
}