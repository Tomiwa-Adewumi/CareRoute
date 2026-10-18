package com.careroute.ingestion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DataHealthController {
    private final IngestionHealthService health;
    public DataHealthController(IngestionHealthService health) { this.health = health; }

    @GetMapping("/health")
    public IngestionHealthService.Status health() { return health.status(); }
}
