package darkchoco.apmapi.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import darkchoco.apmapi.portfolio.domain.TargetRatioCalculator.PlanBasis;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TargetRatioCalculatorTest {

    // 요구사항 문서 4장 예시 버전: planKrw 45,000,000 / planUsd 18,000 / fxUsd 1,350.5 / planEur 4,500
    private static final PlanBasis BASIS = new PlanBasis(
            new BigDecimal("45000000"), new BigDecimal("18000.00"), new BigDecimal("4500.00"), new BigDecimal("1350.50"));

    private static final BigDecimal QUARTER = new BigDecimal("0.25");

    @Test
    void krwItem_matchesTheDocumentedExample() {
        // 분모 0.25 × (45,000,000 + 18,000 × 1,350.5) = 17,327,250, 8,000,000 ÷ 17,327,250 ≈ 0.4617
        BigDecimal ratio = TargetRatioCalculator.calculate(
                HoldingCurrency.KRW, new BigDecimal("8000000"), QUARTER, BigDecimal.ZERO, BASIS);

        assertThat(ratio).isEqualByComparingTo("0.4617");
    }

    @Test
    void usdItem_convertsPlanToKrwWithFxUsd() {
        // 분자 1,000 × 1,350.5 = 1,350,500, 분모 17,327,250 → 0.0779
        BigDecimal ratio = TargetRatioCalculator.calculate(
                HoldingCurrency.USD, new BigDecimal("1000.00"), QUARTER, BigDecimal.ZERO, BASIS);

        assertThat(ratio).isEqualByComparingTo("0.0779");
    }

    @Test
    void eurItem_usesEurTrackOnlyAndDoesNotNeedFxUsd() {
        PlanBasis withoutFx = new PlanBasis(BASIS.planKrw(), BASIS.planUsd(), BASIS.planEur(), null);

        // 분모 0.5 × 4,500 = 2,250, 분자 1,125 → 0.5
        BigDecimal ratio = TargetRatioCalculator.calculate(
                HoldingCurrency.EUR, new BigDecimal("1125.00"), BigDecimal.ZERO, new BigDecimal("0.50"), withoutFx);

        assertThat(ratio).isEqualByComparingTo("0.5");
    }

    @Test
    void returnsNullWhenItemHasNoPlan() {
        assertThat(TargetRatioCalculator.calculate(HoldingCurrency.KRW, null, QUARTER, BigDecimal.ZERO, BASIS))
                .isNull();
    }

    @Test
    void returnsNullWhenDenominatorIsZero() {
        // 대상 비율이 0이거나 계획금이 0인 경우
        assertThat(TargetRatioCalculator.calculate(
                HoldingCurrency.KRW, new BigDecimal("8000000"), BigDecimal.ZERO, BigDecimal.ZERO, BASIS)).isNull();
        assertThat(TargetRatioCalculator.calculate(
                HoldingCurrency.EUR, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ONE,
                new PlanBasis(BASIS.planKrw(), BASIS.planUsd(), null, BASIS.fxUsd()))).isNull();
    }

    @Test
    void krwAndUsdItemsReturnNullWithoutFxUsd() {
        PlanBasis withoutFx = new PlanBasis(BASIS.planKrw(), BASIS.planUsd(), BASIS.planEur(), null);

        assertThat(TargetRatioCalculator.calculate(
                HoldingCurrency.KRW, new BigDecimal("8000000"), QUARTER, BigDecimal.ZERO, withoutFx)).isNull();
        assertThat(TargetRatioCalculator.calculate(
                HoldingCurrency.USD, new BigDecimal("1000"), QUARTER, BigDecimal.ZERO, withoutFx)).isNull();
    }

    @Test
    void treatsMissingBasisAmountsAsZero() {
        PlanBasis krwOnly = new PlanBasis(new BigDecimal("10000000"), null, null, new BigDecimal("1350.50"));

        BigDecimal ratio = TargetRatioCalculator.calculate(
                HoldingCurrency.KRW, new BigDecimal("2500000"), BigDecimal.ONE, null, krwOnly);

        assertThat(ratio).isEqualByComparingTo("0.25");
    }

    @Test
    void ratioMayExceedOneWhenItemPlanIsLargerThanItsTarget() {
        BigDecimal ratio = TargetRatioCalculator.calculate(
                HoldingCurrency.KRW, new BigDecimal("30000000"), QUARTER, BigDecimal.ZERO, BASIS);

        assertThat(ratio).isGreaterThan(BigDecimal.ONE);
    }
}
