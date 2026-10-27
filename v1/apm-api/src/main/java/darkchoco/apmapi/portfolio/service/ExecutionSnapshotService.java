package darkchoco.apmapi.portfolio.service;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** '값 가져오기' - 최신 기준월의 실행 금액과 기준환율을 계산해 돌려준다. DB에는 아무것도 쓰지 않는다. */
@Service
public class ExecutionSnapshotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionSnapshotService.class);

    /** 기준일 전후로 환율을 찾는 범위(일). */
    private static final int FX_SEARCH_DAYS = 3;

    private final ExecutionRepository executionRepository;
    private final ExRateRepository exRateRepository;
    private final TargetRepository targetRepository;
    private final Clock clock;

    public ExecutionSnapshotService(ExecutionRepository executionRepository, ExRateRepository exRateRepository,
                                    TargetRepository targetRepository, Clock clock) {
        this.executionRepository = executionRepository;
        this.exRateRepository = exRateRepository;
        this.targetRepository = targetRepository;
        this.clock = clock;
    }

    /**
     * @param manualFxUsd 수동 입력 환율. manualFxEur와 함께 주면 환율 조회 없이 이 값을 쓴다 (둘 중 하나만 주면 400)
     * @param manualFxEur 수동 입력 환율
     */
    public ExecutionSnapshotResponse getSnapshot(BigDecimal manualFxUsd, BigDecimal manualFxEur) {
        boolean manual = validateManualFx(manualFxUsd, manualFxEur);

        LocalDate baseDate = executionRepository.findLatestBaseDate()
                .orElseThrow(() -> new ResourceNotFoundException("집계할 보유 데이터가 없습니다"));

        ResolvedFx fx = manual
                ? new ResolvedFx(manualFxUsd, manualFxEur, FxSource.MANUAL, null)
                : lookupFx(baseDate);

        LOGGER.debug("값 가져오기: baseDate={}, fxSource={}, fxDate={}", baseDate, fx.source(), fx.date());
        return new ExecutionSnapshotResponse(baseDate, fx.usd(), fx.eur(), fx.source(), fx.date(),
                now(), execForAllTargets());
    }

    // 둘 다 없으면 조회, 둘 다 있으면 수동 입력. 하나만 있으면 어느 쪽을 쓸지 알 수 없으므로 거절한다.
    private boolean validateManualFx(BigDecimal fxUsd, BigDecimal fxEur) {
        if (fxUsd == null && fxEur == null) {
            return false;
        }
        if (fxUsd == null || fxEur == null) {
            throw new InvalidRequestException("fxUsd와 fxEur는 함께 입력해야 합니다");
        }
        checkFx("fxUsd", fxUsd);
        checkFx("fxEur", fxEur);
        return true;
    }

    // 환율은 DECIMAL(12,2)로 저장되므로 소수 2자리까지, 0 이하는 의미가 없다.
    private void checkFx(String field, BigDecimal fx) {
        if (fx.signum() <= 0) {
            throw new InvalidRequestException("%s: 0보다 커야 합니다".formatted(field));
        }
        if (fx.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("%s: 소수 2자리까지만 입력할 수 있습니다".formatted(field));
        }
    }

    // 기준일 전후 3일 이내 최근접 값. 거리가 같으면 기준일 이전(과거) 값을 우선한다 (기준일 시점에 알 수 있던 값).
    private ResolvedFx lookupFx(LocalDate baseDate) {
        Comparator<ExRate> nearestThenEarlier = Comparator
                .comparingLong((ExRate rate) -> Math.abs(ChronoUnit.DAYS.between(baseDate, rate.baseDate())))
                .thenComparing(ExRate::baseDate);

        return exRateRepository.findBetween(baseDate.minusDays(FX_SEARCH_DAYS), baseDate.plusDays(FX_SEARCH_DAYS))
                .stream()
                .min(nearestThenEarlier)
                .map(rate -> new ResolvedFx(rate.usd(), rate.eur(), FxSource.EX_RATE, rate.baseDate()))
                .orElseThrow(() -> new UnprocessableEntityException(
                        "기준일(%s) 전후 %d일 이내에 환율이 없습니다. 환율을 직접 입력하세요 (FX_RATE_MISSING)"
                                .formatted(baseDate, FX_SEARCH_DAYS)));
    }

    // 대상이 늘어도 화면이 깨지지 않도록 대상 전부를 내려주고, 보유 건이 없는 대상은 0으로 채운다.
    private List<ExecAmountDto> execForAllTargets() {
        Map<Integer, ExecAmountDto> amountBySchemeAcId = executionRepository.findExecAmounts().stream()
                .collect(Collectors.toMap(ExecAmountDto::schemeAcId, Function.identity()));

        return targetRepository.findAll().stream()
                .map(TargetResponse::schemeAcId)
                .map(schemeAcId -> amountBySchemeAcId.getOrDefault(schemeAcId, zeroAmount(schemeAcId)))
                .toList();
    }

    private ExecAmountDto zeroAmount(int schemeAcId) {
        return new ExecAmountDto(schemeAcId, 0L, new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    // 초 미만은 버린다 (버전 저장 시 fetch_date로 그대로 쓰일 수 있는 값이라 DATETIME에 맞춘다).
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private record ResolvedFx(BigDecimal usd, BigDecimal eur, FxSource source, LocalDate date) {
    }
}
