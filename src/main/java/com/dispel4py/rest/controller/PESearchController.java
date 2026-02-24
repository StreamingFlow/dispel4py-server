package com.dispel4py.rest.controller;

import com.dispel4py.rest.model.PEFts;
import com.dispel4py.rest.service.PESearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/registry/{user}/pe/search")
public class PESearchController {

    private final PESearchService peSearchService;

    public PESearchController(PESearchService peSearchService) {
        this.peSearchService = peSearchService;
    }

    @PostMapping
    public PEFts index(@RequestBody PEFts data) {
        return peSearchService.index(data);
    }

    @GetMapping
    public List<PEFts> search(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        return peSearchService.search(q, limit);
    }

    @DeleteMapping
    public void delete(@PathVariable("user") String user, @RequestParam("id") Long id) {
        peSearchService.delete(id);
    }
}