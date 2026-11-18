package com.sskings.bolso.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/api/workspaces/{ws}/dashboard")
    public DashboardService.Dashboard dashboard(@PathVariable UUID ws, @RequestParam(required = false) YearMonth month) {
        return service.month(ws, month);
    }
}
