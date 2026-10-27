package darkchoco.apmapi.portfolio.dto;

import java.time.LocalDateTime;

public record VersionSummaryResponse(int versionId, String versionLabel, LocalDateTime updatedAt) {
}
