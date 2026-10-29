package com.flowdesk.dashboard;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@Tag(name = "Dashboard & reports")
public class DashboardController {

    private final DashboardService dashboard;
    private final ReportService reports;

    public DashboardController(DashboardService dashboard, ReportService reports) {
        this.dashboard = dashboard;
        this.reports = reports;
    }

    @GetMapping("/dashboard/summary")
    public DashboardDtos.Summary summary(@RequestParam(defaultValue = "30") int days) {
        return dashboard.summary(days);
    }

    @GetMapping("/reports/productivity")
    public List<DashboardDtos.TechnicianProductivity> productivity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.productivity(from, to);
    }

    @GetMapping("/reports/service-orders.csv")
    public ResponseEntity<byte[]> serviceOrdersCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = reports.serviceOrdersCsv(from, to).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("service-orders.csv").build().toString())
                .body(body);
    }
}
