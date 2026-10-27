package darkchoco.apmapi.portfolio.controller;

import darkchoco.apmapi.portfolio.dto.TargetResponse;
import darkchoco.apmapi.portfolio.service.TargetService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/targets")
public class TargetController {

    private final TargetService targetService;

    public TargetController(TargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping
    public List<TargetResponse> getTargets() {
        return targetService.getTargets();
    }
}
