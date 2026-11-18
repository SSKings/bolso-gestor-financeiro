package com.sskings.bolso.forecast;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService service;

    @GetMapping("/api/workspaces/{ws}/forecast")
    public ForecastService.Forecast forecast(@PathVariable UUID ws) {
        return service.current(ws);
    }
}
