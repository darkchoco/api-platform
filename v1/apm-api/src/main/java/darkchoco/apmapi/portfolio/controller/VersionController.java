package darkchoco.apmapi.portfolio.controller;

import darkchoco.apmapi.portfolio.dto.NextLabelsResponse;
import darkchoco.apmapi.portfolio.dto.VersionCreatedResponse;
import darkchoco.apmapi.portfolio.dto.VersionDetailResponse;
import darkchoco.apmapi.portfolio.dto.VersionSaveRequest;
import darkchoco.apmapi.portfolio.dto.VersionSummaryResponse;
import darkchoco.apmapi.portfolio.service.VersionService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio/versions")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    public List<VersionSummaryResponse> getVersions() {
        return versionService.getVersions();
    }

    @GetMapping("/latest")
    public VersionDetailResponse getLatestVersion() {
        return versionService.getLatestVersion();
    }

    @GetMapping("/next-labels")
    public NextLabelsResponse getNextLabels(@RequestParam(required = false) Integer baseVersionId) {
        return versionService.getNextLabels(baseVersionId);
    }

    @GetMapping("/{versionId}")
    public VersionDetailResponse getVersion(@PathVariable int versionId) {
        return versionService.getVersion(versionId);
    }

    @PostMapping
    public ResponseEntity<VersionCreatedResponse> createVersion(@RequestBody VersionSaveRequest request) {
        VersionCreatedResponse created = versionService.createVersion(request);
        return ResponseEntity.created(URI.create("/api/portfolio/versions/" + created.versionId())).body(created);
    }

    @PutMapping("/{versionId}")
    public VersionDetailResponse updateVersion(@PathVariable int versionId, @RequestBody VersionSaveRequest request) {
        return versionService.updateVersion(versionId, request);
    }
}
