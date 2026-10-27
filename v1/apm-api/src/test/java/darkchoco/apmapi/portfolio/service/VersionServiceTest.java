package darkchoco.apmapi.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import darkchoco.apmapi.common.exception.ConflictException;
import darkchoco.apmapi.common.exception.InvalidRequestException;
import darkchoco.apmapi.common.exception.ResourceNotFoundException;
import darkchoco.apmapi.portfolio.dto.NextLabelsResponse;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.dto.TargetResponse;
import darkchoco.apmapi.portfolio.dto.VersionCreatedResponse;
import darkchoco.apmapi.portfolio.dto.VersionDetailResponse;
import darkchoco.apmapi.portfolio.dto.VersionSaveRequest;
import darkchoco.apmapi.portfolio.repository.TargetRepository;
import darkchoco.apmapi.portfolio.repository.VersionHeader;
import darkchoco.apmapi.portfolio.repository.VersionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private TargetRepository targetRepository;

    private VersionService service;

    @BeforeEach
    void setUp() {
        // 2026-11-05 10:00:00.5 (Asia/Seoul) - 월이 바뀐 시점을 가정한다
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T01:00:00.500Z"), SEOUL);
        service = new VersionService(versionRepository, targetRepository, new VersionSaveValidator(), clock);
    }

    @Test
    void createVersion_withoutBase_usesThisMonthZeroLabel() {
        givenTargets(1, 2);
        when(versionRepository.existsByLabel("v2611.0")).thenReturn(false);
        when(versionRepository.insert(eq("v2611.0"), any(), eq(LocalDateTime.of(2026, 11, 5, 10, 0, 0)))).thenReturn(7);

        VersionCreatedResponse created = service.createVersion(validRequest(null));

        assertThat(created).isEqualTo(new VersionCreatedResponse(7, "v2611.0"));
        verify(versionRepository).replacePlanDetails(eq(7), any());
    }

    @Test
    void createVersion_withoutBase_conflictsWhenThisMonthZeroExists() {
        givenTargets(1, 2);
        when(versionRepository.existsByLabel("v2611.0")).thenReturn(true);

        assertThatThrownBy(() -> service.createVersion(validRequest(null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("v2611.0");
        verify(versionRepository, never()).insert(any(), any(), any());
    }

    @Test
    void createVersion_withBase_bumpsSerialKeepingBasePrefix() {
        givenTargets(1, 2);
        when(versionRepository.findHeader(3)).thenReturn(Optional.of(header(3, "v2610.3")));
        when(versionRepository.findLabelsByPrefix("v2610.")).thenReturn(List.of("v2610.0", "v2610.3"));
        when(versionRepository.insert(eq("v2610.4"), any(), any())).thenReturn(8);

        VersionCreatedResponse created = service.createVersion(validRequest(3));

        assertThat(created.versionLabel()).isEqualTo("v2610.4");
    }

    @Test
    void createVersion_withMissingBase_isNotFound() {
        givenTargets(1, 2);
        when(versionRepository.findHeader(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVersion(validRequest(99)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createVersion_withNonStandardBaseLabel_isBadRequest() {
        givenTargets(1, 2);
        when(versionRepository.findHeader(1)).thenReturn(Optional.of(header(1, "초기버전")));

        assertThatThrownBy(() -> service.createVersion(validRequest(1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createVersion_translatesConcurrentLabelCollisionToConflict() {
        givenTargets(1, 2);
        when(versionRepository.existsByLabel("v2611.0")).thenReturn(false);
        when(versionRepository.insert(any(), any(), any())).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.createVersion(validRequest(null)))
                .isInstanceOf(ConflictException.class);
        verify(versionRepository, never()).replacePlanDetails(anyInt(), any());
    }

    @Test
    void updateVersion_ofMissingVersion_isNotFound() {
        givenTargets(1, 2);
        when(versionRepository.findHeader(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateVersion(99, validRequest(null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(versionRepository, never()).update(anyInt(), any(), any());
    }

    @Test
    void getVersion_fillsMissingTargetsWithZeroRatioAndNullExec() {
        givenTargets(1, 2);
        when(versionRepository.findHeader(3)).thenReturn(Optional.of(header(3, "v2610.0")));
        when(versionRepository.findPlanDetails(3)).thenReturn(List.of(
                new PlanDetailDto(1, new BigDecimal("0.250000"), new BigDecimal("0.000000"), 15_000_000L, null, null)));

        VersionDetailResponse response = service.getVersion(3);

        assertThat(response.detail()).hasSize(2);
        assertThat(response.detail().get(0).execKrw()).isEqualTo(15_000_000L);
        assertThat(response.detail().get(1))
                .isEqualTo(new PlanDetailDto(2, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null));
    }

    @Test
    void getLatestVersion_isNotFoundWhenNoVersionExists() {
        when(versionRepository.findLatestVersionId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestVersion()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getNextLabels_withoutBase_returnsOnlyThisMonth() {
        when(versionRepository.existsByLabel("v2611.0")).thenReturn(true);

        NextLabelsResponse labels = service.getNextLabels(null);

        assertThat(labels.thisMonth()).isEqualTo(new NextLabelsResponse.ThisMonth("v2611.0", false));
        assertThat(labels.fromBase()).isNull();
    }

    @Test
    void getNextLabels_withBase_returnsBumpedLabelAndBaseLabel() {
        when(versionRepository.existsByLabel("v2611.0")).thenReturn(false);
        when(versionRepository.findHeader(3)).thenReturn(Optional.of(header(3, "v2610.3")));
        when(versionRepository.findLabelsByPrefix("v2610.")).thenReturn(List.of("v2610.3"));

        NextLabelsResponse labels = service.getNextLabels(3);

        assertThat(labels.thisMonth()).isEqualTo(new NextLabelsResponse.ThisMonth("v2611.0", true));
        assertThat(labels.fromBase()).isEqualTo(new NextLabelsResponse.FromBase("v2610.4", "v2610.3"));
    }

    private void givenTargets(int... schemeAcIds) {
        List<TargetResponse> targets = Arrays.stream(schemeAcIds)
                .mapToObj(id -> new TargetResponse(id, "대상" + id))
                .toList();
        when(targetRepository.findAll()).thenReturn(targets);
    }

    private VersionSaveRequest validRequest(Integer baseVersionId) {
        return new VersionSaveRequest(baseVersionId, null, null, null, 10_000_000L, null, null, null, null, null,
                List.of(
                        new PlanDetailDto(1, new BigDecimal("0.60"), BigDecimal.ZERO, null, null, null),
                        new PlanDetailDto(2, new BigDecimal("0.40"), BigDecimal.ZERO, null, null, null)));
    }

    private VersionHeader header(int versionId, String label) {
        return new VersionHeader(versionId, label, null, null, null, null, null, null, null, null, null,
                LocalDateTime.of(2026, 10, 1, 0, 0), null);
    }
}
