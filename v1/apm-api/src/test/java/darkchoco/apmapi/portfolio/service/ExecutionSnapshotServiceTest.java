package darkchoco.apmapi.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import darkchoco.apmapi.common.exception.ResourceNotFoundException;
import darkchoco.apmapi.common.exception.UnprocessableEntityException;
import darkchoco.apmapi.portfolio.domain.FxSource;
import darkchoco.apmapi.portfolio.dto.ExecAmountDto;
import darkchoco.apmapi.portfolio.dto.ExecutionSnapshotResponse;
import darkchoco.apmapi.portfolio.dto.TargetResponse;
import darkchoco.apmapi.portfolio.repository.ExRate;
import darkchoco.apmapi.portfolio.repository.ExRateRepository;
import darkchoco.apmapi.portfolio.repository.ExecutionRepository;
import darkchoco.apmapi.portfolio.repository.TargetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
class ExecutionSnapshotServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 15);

    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private ExRateRepository exRateRepository;
    @Mock
    private TargetRepository targetRepository;

    private ExecutionSnapshotService service;

    @BeforeEach
    void setUp() {
        // 2026-10-25 09:12:00.7 (Asia/Seoul)
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T00:12:00.700Z"), ZoneId.of("Asia/Seoul"));
        service = new ExecutionSnapshotService(executionRepository, exRateRepository, targetRepository, clock);
    }

    @Test
    void getSnapshot_usesNearestExRateWithinThreeDaysOfBaseDate() {
        givenBaseDateAndTargets(1);
        when(exRateRepository.findBetween(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18))).thenReturn(List.of(
                rate("2026-09-12", "1300.00"), rate("2026-09-14", "1362.11"), rate("2026-09-18", "1400.00")));

        ExecutionSnapshotResponse response = service.getSnapshot(null, null);

        assertThat(response.baseDate()).isEqualTo(BASE_DATE);
        assertThat(response.fxSource()).isEqualTo(FxSource.EX_RATE);
        assertThat(response.fxDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(response.fxUsd()).isEqualByComparingTo("1362.11");
        assertThat(response.fxEur()).isEqualByComparingTo("1572.28");
    }

    @Test
    void getSnapshot_prefersEarlierDateWhenDistanceIsTied() {
        givenBaseDateAndTargets(1);
        when(exRateRepository.findBetween(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18))).thenReturn(List.of(
                rate("2026-09-13", "1310.00"), rate("2026-09-17", "1390.00")));

        ExecutionSnapshotResponse response = service.getSnapshot(null, null);

        assertThat(response.fxDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(response.fxUsd()).isEqualByComparingTo("1310.00");
    }

    @Test
    void getSnapshot_prefersExactBaseDateOverNeighbours() {
        givenBaseDateAndTargets(1);
        when(exRateRepository.findBetween(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18))).thenReturn(List.of(
                rate("2026-09-14", "1310.00"), rate("2026-09-15", "1362.11"), rate("2026-09-16", "1390.00")));

        assertThat(service.getSnapshot(null, null).fxDate()).isEqualTo(BASE_DATE);
    }

    @Test
    void getSnapshot_isUnprocessableWhenNoExRateWithinThreeDays() {
        givenBaseDateAndTargets();
        when(exRateRepository.findBetween(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 18))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getSnapshot(null, null))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("FX_RATE_MISSING");
    }

    @Test
    void getSnapshot_withManualFx_skipsExRateLookup() {
        givenBaseDateAndTargets(1);

        ExecutionSnapshotResponse response = service.getSnapshot(new BigDecimal("1350.50"), new BigDecimal("1470.2"));

        assertThat(response.fxSource()).isEqualTo(FxSource.MANUAL);
        assertThat(response.fxDate()).isNull();
        assertThat(response.fxUsd()).isEqualByComparingTo("1350.50");
        assertThat(response.fxEur()).isEqualByComparingTo("1470.20");
        verifyNoInteractions(exRateRepository);
    }

    @Test
    void getSnapshot_rejectsOnlyOneManualFx() {
        assertThatThrownBy(() -> service.getSnapshot(new BigDecimal("1350.50"), null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.getSnapshot(null, new BigDecimal("1470.20")))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(executionRepository, exRateRepository);
    }

    @Test
    void getSnapshot_rejectsNonPositiveOrTooPreciseManualFx() {
        assertThatThrownBy(() -> service.getSnapshot(BigDecimal.ZERO, new BigDecimal("1470.20")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("fxUsd");
        assertThatThrownBy(() -> service.getSnapshot(new BigDecimal("1350.50"), new BigDecimal("-1")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("fxEur");
        assertThatThrownBy(() -> service.getSnapshot(new BigDecimal("1350.505"), new BigDecimal("1470.20")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("fxUsd");
    }

    @Test
    void getSnapshot_isNotFoundWhenThereIsNoHoldingData() {
        when(executionRepository.findLatestBaseDate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSnapshot(null, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(exRateRepository);
    }

    @Test
    void getSnapshot_fillsTargetsWithoutHoldingsWithZeroAndKeepsSchemeAcIdOrder() {
        givenBaseDateAndTargets(1, 2, 3);
        when(executionRepository.findExecAmounts()).thenReturn(List.of(
                new ExecAmountDto(3, 1_000L, new BigDecimal("12.50"), new BigDecimal("0.00")),
                new ExecAmountDto(1, 563_765_300L, new BigDecimal("0.00"), new BigDecimal("0.00"))));

        ExecutionSnapshotResponse response = service.getSnapshot(new BigDecimal("1350.50"), new BigDecimal("1470.20"));

        assertThat(response.exec()).extracting(ExecAmountDto::schemeAcId).containsExactly(1, 2, 3);
        assertThat(response.exec().get(0).execKrw()).isEqualTo(563_765_300L);
        assertThat(response.exec().get(1))
                .isEqualTo(new ExecAmountDto(2, 0L, new BigDecimal("0.00"), new BigDecimal("0.00")));
        assertThat(response.exec().get(2).execUsd()).isEqualByComparingTo("12.50");
    }

    @Test
    void getSnapshot_stampsFetchDateFromClockWithoutFractionalSeconds() {
        givenBaseDateAndTargets();

        ExecutionSnapshotResponse response = service.getSnapshot(new BigDecimal("1350.50"), new BigDecimal("1470.20"));

        assertThat(response.fetchDate()).isEqualTo(LocalDateTime.of(2026, 10, 25, 9, 12, 0));
        verify(executionRepository).findExecAmounts();
    }

    // 환율 조회가 422로 끝나는 테스트는 집계/대상 조회까지 가지 않으므로 해당 스텁은 lenient로 둔다.
    private void givenBaseDateAndTargets(int... schemeAcIds) {
        when(executionRepository.findLatestBaseDate()).thenReturn(Optional.of(BASE_DATE));
        lenient().when(executionRepository.findExecAmounts()).thenReturn(List.of());
        lenient().when(targetRepository.findAll()).thenReturn(Arrays.stream(schemeAcIds)
                .mapToObj(id -> new TargetResponse(id, "대상" + id))
                .toList());
    }

    private ExRate rate(String date, String usd) {
        return new ExRate(LocalDate.parse(date), new BigDecimal(usd), new BigDecimal("1572.28"));
    }
}
