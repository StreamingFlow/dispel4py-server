package com.dispel4py.rest.service;

import com.dispel4py.rest.model.Execution;
import com.dispel4py.rest.model.PE;
import com.dispel4py.rest.model.Workflow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.logging.Logger;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

@Service
public class ExecutionServiceImpl implements ExecutionService {
    WorkflowService workflowService;

    @Autowired
    private Environment env;

    @Autowired
    public ExecutionServiceImpl(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public void sendResources(MultipartFile[] files, String user) {
        Logger logger = Logger.getLogger(getClass().getName());
        String url = env.getProperty("laminar.execution.url");
        WebClient webClient = WebClient.create(url);

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("user", user);
        for (MultipartFile file : files) {
            try {
                builder.part("files", new InputStreamResource(file.getInputStream()), MediaType.APPLICATION_OCTET_STREAM)
                       .filename(file.getOriginalFilename());
                logger.info("Adding file to multipart: " + file.getOriginalFilename());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        logger.info("Sending resources to URL: " + url + "/resource for user: " + user);

        Mono<Void> result = webClient.put()
                .uri(uriBuilder -> uriBuilder.path("/resource").build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Void.class);

        result.subscribe(
                success -> logger.info("Resources uploaded successfully"),
                error -> {
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webClientResponseException = (WebClientResponseException) error;
                        logger.severe("Error uploading resources: " + webClientResponseException.getStatusCode() + " - " + webClientResponseException.getResponseBodyAsString());
                    } else {
                        logger.severe("Error uploading resources: " + error.getMessage());
                    }
                }
        );

        logger.info("After sending resources to URL: " + url + "/resource for user: " + user);
    }

    public Flux<String> runWorkflow(Execution e, String user) {

        //e.imports will already be set from client for direct execution

        Long workflowId = e.getWorkflowId();
        String workflowName = e.getWorkflowName();
        Workflow wf;

        e.setUser(user);

        if (!(workflowId == null)) {
            wf = workflowService.getWorkflowByID(workflowId, user);
            e.setGraph(wf);

            //Set import string from joining pe imports
            String imports = "";
            for (PE pe : wf.getPEs()) {
                String s = pe.getPeImports();
                System.out.println("" + s);
                if (!s.equals("")) {
                    imports = imports + "," + s;
                }
            }
            e.setModuleSourceCode(wf.getModuleSourceCode());
            e.setImports(imports);

        } else if (!(workflowName == null)) {
            wf = workflowService.getWorkflowByName(workflowName, user);
            e.setGraph(wf);
            String imports = "";
            for (PE pe : wf.getPEs()) {
                String s = pe.getPeImports();
                System.out.println("" + s);
                if (!s.equals("")) {
                    imports = imports + "," + s;
                }
            }
            e.setImports(imports);
            e.setModuleSourceCode(wf.getModuleSourceCode());
        }

        System.out.println(e);

        //WebClient webClient = WebClient.create("https://executionengined4py.azurewebsites.net");
        String url = env.getProperty("laminar.execution.url");
        WebClient webClient = WebClient.create(url);

        Flux<String> result = webClient.post()
                .uri("/run")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Mono.just(e), Execution.class)
                .retrieve()
                .bodyToFlux(String.class)
                .log();
                //.bodyToMono(String.class).block();

        System.out.println(result);
        result.subscribe(System.out::println);

        return result;

    }
}

