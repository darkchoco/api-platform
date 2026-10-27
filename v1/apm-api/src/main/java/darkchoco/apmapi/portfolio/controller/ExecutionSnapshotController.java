package darkchoco.apmapi.portfolio.controller;

import darkchoco.apmapi.portfolio.dto.ExecutionSnapshotResponse;
import darkchoco.apmapi.portfolio.service.ExecutionSnapshotService;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/execution-snapshot")
public class ExecutionSnapshotController {

    private final ExecutionSnapshotService executionSnapshotService;

    public ExecutionSnapshotController(ExecutionSnapshotService executionSnapshotService) {
        this.executionSnapshotService = executionSnapshotService;
    }

    /** fxUsd·fxEur를 함께 주면 환율 조회 없이 그 값을 쓴다 (환율 누락 422 이후 수동 입력 재호출용). */
    @GetMapping
    public ExecutionSnapshotResponse getSnapshot(@RequestParam(required = false) BigDecimal fxUsd,
                                                 @RequestParam(required = false) BigDecimal fxEur) {
        return executionSnapshotService.getSnapshot(fxUsd, fxEur);
    }
}
