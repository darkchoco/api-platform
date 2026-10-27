package darkchoco.apmapi.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 종목의 대상내 비율(톱다운): 종목 투자계획금액 ÷ 그 종목이 속한 대상의 'Portfolio 구성' 계획금액.
 * 기준은 가장 최근 버전이며, 분모를 계산할 수 없으면 null이다 (오류가 아니다).
 */
public final class TargetRatioCalculator {

    private static final int SCALE = 4;

    private TargetRatioCalculator() {
    }

    /** 최근 버전 헤더의 계획금과 환율. 값이 없는(null) 항목은 0으로 본다. */
    public record PlanBasis(BigDecimal planKrw, BigDecimal planUsd, BigDecimal planEur, BigDecimal fxUsd) {
    }

    /**
     * @param currency    종목의 통화
     * @param planAmount  종목 투자계획금액 (종목 통화 기준). 계획이 없으면 null
     * @param krwUsdRatio 종목이 속한 대상의 원화+달러 투자비율
     * @param eurRatio    종목이 속한 대상의 유로 투자비율
     * @return 0~1 소수(소수 4자리), 계산할 수 없으면 null
     */
    public static BigDecimal calculate(HoldingCurrency currency, BigDecimal planAmount,
                                       BigDecimal krwUsdRatio, BigDecimal eurRatio, PlanBasis basis) {
        if (planAmount == null) {
            return null;
        }

        BigDecimal numerator;
        BigDecimal denominator;
        if (currency == HoldingCurrency.EUR) {
            numerator = planAmount;
            denominator = orZero(eurRatio).multiply(orZero(basis.planEur()));
        } else {
            // 원화·달러 종목은 달러 계획금을 원화로 환산해 한 트랙으로 계산하므로 fxUsd가 꼭 필요하다.
            if (basis.fxUsd() == null) {
                return null;
            }
            numerator = currency == HoldingCurrency.KRW ? planAmount : planAmount.multiply(basis.fxUsd());
            BigDecimal trackPlan = orZero(basis.planKrw()).add(orZero(basis.planUsd()).multiply(basis.fxUsd()));
            denominator = orZero(krwUsdRatio).multiply(trackPlan);
        }

        if (denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
