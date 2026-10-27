package darkchoco.apmapi.portfolio.service;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.dto.VersionSaveRequest;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 버전 저장 요청의 업무 규칙 검증. 위반하면 첫 번째 위반 내용으로 {@link InvalidRequestException}(400)을 던진다.
 * 비율 비교는 부동소수점이 아니라 항상 {@link BigDecimal}로 한다.
 */
@Component
public class VersionSaveValidator {

    private static final long MAX_KRW = 999_999_999_999L; // DECIMAL(12)
    private static final BigDecimal MAX_FOREIGN = new BigDecimal("9999999999.99"); // DECIMAL(12,2)
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public void validate(VersionSaveRequest request, Set<Integer> targetIds) {
        validateAmounts(request);

        List<PlanDetailDto> detail = request.detail();
        if (detail == null) {
            throw new InvalidRequestException("detail: 필수 값입니다");
        }
        validateTargets(detail, targetIds);
        validateExecAmounts(detail);
        validateRatios(detail);
        validateRatioSum("krwUsdRatio", detail, PlanDetailDto::krwUsdRatio,
                isUnused(request.planKrw()) && isUnused(request.planUsd()));
        validateRatioSum("eurRatio", detail, PlanDetailDto::eurRatio, isUnused(request.planEur()));
    }

    private void validateAmounts(VersionSaveRequest request) {
        checkKrw("availKrw", request.availKrw());
        checkForeign("availUsd", request.availUsd());
        checkForeign("availEur", request.availEur());
        checkKrw("planKrw", request.planKrw());
        checkForeign("planUsd", request.planUsd());
        checkForeign("planEur", request.planEur());
        checkForeign("fxUsd", request.fxUsd());
        checkForeign("fxEur", request.fxEur());
    }

    private void validateExecAmounts(List<PlanDetailDto> detail) {
        for (PlanDetailDto row : detail) {
            checkKrw("detail.execKrw", row.execKrw());
            checkForeign("detail.execUsd", row.execUsd());
            checkForeign("detail.execEur", row.execEur());
        }
    }

    private void validateTargets(List<PlanDetailDto> detail, Set<Integer> targetIds) {
        Set<Integer> seen = new HashSet<>();
        for (PlanDetailDto row : detail) {
            if (!targetIds.contains(row.schemeAcId())) {
                throw new InvalidRequestException("detail.schemeAcId: 존재하지 않는 대상입니다 (%d)".formatted(row.schemeAcId()));
            }
            if (!seen.add(row.schemeAcId())) {
                throw new InvalidRequestException("detail.schemeAcId: 중복된 대상입니다 (%d)".formatted(row.schemeAcId()));
            }
        }
    }

    private void validateRatios(List<PlanDetailDto> detail) {
        for (PlanDetailDto row : detail) {
            checkRatio("detail.krwUsdRatio", row.krwUsdRatio());
            checkRatio("detail.eurRatio", row.eurRatio());
        }
    }

    // 비율은 0~1이고 정수 %만 허용한다: ratio × 100이 정수여야 한다 (0.33 O, 0.333 X).
    private void checkRatio(String field, BigDecimal ratio) {
        if (ratio == null) {
            return;
        }
        if (ratio.signum() < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidRequestException("%s: 0 이상 1 이하여야 합니다".formatted(field));
        }
        if (ratio.multiply(HUNDRED).stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("%s: 정수 %%만 입력할 수 있습니다 (예: 0.33)".formatted(field));
        }
    }

    // 트랙 합계는 정확히 1(=100%)이어야 한다. 그 트랙의 계획금을 쓰지 않으면 합 0도 허용한다.
    private void validateRatioSum(String field, List<PlanDetailDto> detail,
                                  Function<PlanDetailDto, BigDecimal> ratioOf, boolean trackUnused) {
        BigDecimal sum = detail.stream()
                .map(ratioOf)
                .filter(ratio -> ratio != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean valid = sum.compareTo(BigDecimal.ONE) == 0 || (trackUnused && sum.signum() == 0);
        if (!valid) {
            throw new InvalidRequestException("%s: 합계가 100%%여야 합니다 (현재 %s%%)"
                    .formatted(field, sum.multiply(HUNDRED).stripTrailingZeros().toPlainString()));
        }
    }

    private boolean isUnused(Number planAmount) {
        return planAmount == null || new BigDecimal(planAmount.toString()).signum() == 0;
    }

    private void checkKrw(String field, Long amount) {
        if (amount == null) {
            return;
        }
        if (amount < 0) {
            throw new InvalidRequestException("%s: 0 이상이어야 합니다".formatted(field));
        }
        if (amount > MAX_KRW) {
            throw new InvalidRequestException("%s: 허용 범위를 초과했습니다".formatted(field));
        }
    }

    // 달러·유로 금액과 환율은 DECIMAL(12,2) - 소수 2자리까지.
    private void checkForeign(String field, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        if (amount.signum() < 0) {
            throw new InvalidRequestException("%s: 0 이상이어야 합니다".formatted(field));
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("%s: 소수 2자리까지만 입력할 수 있습니다".formatted(field));
        }
        if (amount.compareTo(MAX_FOREIGN) > 0) {
            throw new InvalidRequestException("%s: 허용 범위를 초과했습니다".formatted(field));
        }
    }
}
