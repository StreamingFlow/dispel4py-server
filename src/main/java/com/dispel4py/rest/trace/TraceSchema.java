package com.dispel4py.rest.trace;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@DependsOn("entityManagerFactory")
public class TraceSchema {
    private final DataSource dataSource;
    public TraceSchema(DataSource dataSource) { this.dataSource = dataSource; }
    @PostConstruct
    public void initialize() {
        new ResourceDatabasePopulator(new ClassPathResource("db/trace_schema.sql")).execute(dataSource);
    }
}
