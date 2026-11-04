package darkchoco.apmapi.alert.controller;

import darkchoco.apmapi.alert.dto.AlertRefreshResponse;
import darkchoco.apmapi.alert.dto.AlertResponse;
import darkchoco.apmapi.alert.dto.DismissAllResponse;
import darkchoco.apmapi.alert.service.AlertService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> getAlerts() {
        return alertService.getAlerts();
    }

    @PostMapping("/{alertId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable int alertId) {
        alertService.dismiss(alertId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dismiss-all")
    public DismissAllResponse dismissAll() {
        return alertService.dismissAll();
    }

    @PostMapping("/refresh")
    public AlertRefreshResponse refresh() {
        return alertService.refreshAll();
    }
}
