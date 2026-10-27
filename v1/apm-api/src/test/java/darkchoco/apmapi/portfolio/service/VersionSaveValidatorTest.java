package darkchoco.apmapi.portfolio.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.dto.VersionSaveRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VersionSaveValidatorTest {

    private static final Set<Integer> TARGET_IDS = Set.of(1, 2, 3);

    private final VersionSaveValidator validator = new VersionSaveValidator();

    @Test
    void acceptsIntegerPercentRatiosSummingToOneHundred() {
        // 33/33/34 - 사용자가 직접 맞춘 정수 %
        var request = request(10_000_000L, 0L,
                detail(1, "0.33", "1"), detail(2, "0.33", "0"), detail(3, "0.34", "0"));

        assertThatCode(() -> validator.validate(request, TARGET_IDS)).doesNotThrowAnyException();
    }

    @Test
    void rejectsRatioWithFractionalPercent() {
        var request = request(10_000_000L, 0L,
                detail(1, "0.333", "1"), detail(2, "0.333", "0"), detail(3, "0.334", "0"));

        assertThatThrownBy(() -> validator.validate(request, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("정수");
    }

    @Test
    void rejectsRatioOutOfRange() {
        var request = request(10_000_000L, 0L, detail(1, "1.01", "1"), detail(2, "-0.01", "0"));

        assertThatThrownBy(() -> validator.validate(request, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsKrwUsdSumNotOneHundredPercent() {
        var request = request(10_000_000L, 0L, detail(1, "0.50", "1"), detail(2, "0.49", "0"));

        assertThatThrownBy(() -> validator.validate(request, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("krwUsdRatio");
    }

    @Test
    void rejectsEurSumNotOneHundredPercentWhenEurPlanIsUsed() {
        var request = request(10_000_000L, 5_000L, detail(1, "1", "0.50"), detail(2, "0", "0.40"));

        assertThatThrownBy(() -> validator.validate(request, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("eurRatio");
    }

    @Test
    void allowsZeroEurSumWhenEurPlanIsUnused() {
        var nullPlan = request(10_000_000L, null, detail(1, "1", "0"), detail(2, "0", "0"));
        var zeroPlan = request(10_000_000L, 0L, detail(1, "1", "0"), detail(2, "0", "0"));

        assertThatCode(() -> validator.validate(nullPlan, TARGET_IDS)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(zeroPlan, TARGET_IDS)).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroSumWhenTrackIsUsed() {
        var request = request(10_000_000L, 0L, detail(1, "0", "0"), detail(2, "0", "0"));

        assertThatThrownBy(() -> validator.validate(request, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("krwUsdRatio");
    }

    @Test
    void rejectsUnknownAndDuplicateTargets() {
        var unknown = request(10_000_000L, 0L, detail(1, "0.5", "0"), detail(9, "0.5", "0"));
        var duplicate = request(10_000_000L, 0L, detail(1, "0.5", "0"), detail(1, "0.5", "0"));

        assertThatThrownBy(() -> validator.validate(unknown, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("존재하지 않는 대상");
        assertThatThrownBy(() -> validator.validate(duplicate, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("중복");
    }

    @Test
    void rejectsNegativeAmountsAndWrongScale() {
        var negative = new VersionSaveRequest(null, -1L, null, null, 10_000_000L, null, null, null, null, null,
                List.of(detail(1, "1", "0")));
        var tooManyDecimals = new VersionSaveRequest(null, null, new BigDecimal("1.234"), null, 10_000_000L, null,
                null, null, null, null, List.of(detail(1, "1", "0")));

        assertThatThrownBy(() -> validator.validate(negative, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("availKrw");
        assertThatThrownBy(() -> validator.validate(tooManyDecimals, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("availUsd");
    }

    @Test
    void rejectsMissingDetail() {
        var request = new VersionSaveRequest(null, null, null, null, 10_000_000L, null, null, null, null, null, null);

        assertThatThrownBy(() -> validator.validate(request, TARGET_IDS))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("detail");
    }

    private VersionSaveRequest request(Long planKrw, Long planEur, PlanDetailDto... detail) {
        BigDecimal eur = planEur == null ? null : BigDecimal.valueOf(planEur);
        return new VersionSaveRequest(null, null, null, null, planKrw, null, eur, null, null, null, List.of(detail));
    }

    private PlanDetailDto detail(int schemeAcId, String krwUsdRatio, String eurRatio) {
        return new PlanDetailDto(schemeAcId, new BigDecimal(krwUsdRatio), new BigDecimal(eurRatio), null, null, null);
    }
}
