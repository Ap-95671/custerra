package com.nci.backend.history;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/history/earthquakes")
public class HistoryController {
    private final HistoryService service;
    public HistoryController(HistoryService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public HistorySnapshot create(@Valid @RequestBody HistoryQuery query) { return service.create(query); }
    @GetMapping("/{id}") public HistorySnapshot get(@PathVariable UUID id) { return service.get(id); }
}
