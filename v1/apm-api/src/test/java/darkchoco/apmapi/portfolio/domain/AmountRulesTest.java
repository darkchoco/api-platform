package darkchoco.apmapi.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountRulesTest {

    @Test
    void krw_acceptsIntegersIncludingTrailingZeroDecimalsAndBounds() {
        assertThatCode(() -> AmountRules.requireKrw("f", new BigDecimal("0"))).doesNotThrowAnyException();
        assertThatCode(() -> AmountRules.requireKrw("f", new BigDecimal("8000000.00"))).doesNotThrowAnyException();
        assertThatCode(() -> AmountRules.requireKrw("f", new BigDecimal("999999999999"))).doesNotThrowAnyException();
        assertThatCode(() -> AmountRules.requireKrw("f", null)).doesNotThrowAnyException();
    }

    @Test
    void krw_rejectsNegativeFractionalAndOverflow() {
        assertThatThrownBy(() -> AmountRules.requireKrw("f", new BigDecimal("-1")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("0 이상");
        assertThatThrownBy(() -> AmountRules.requireKrw("f", new BigDecimal("1.5")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("정수");
        assertThatThrownBy(() -> AmountRules.requireKrw("f", new BigDecimal("1000000000000")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("범위");
    }

    @Test
    void foreign_acceptsUpToTwoDecimalsAndBounds() {
        assertThatCode(() -> AmountRules.requireForeign("f", new BigDecimal("0.01"))).doesNotThrowAnyException();
        assertThatCode(() -> AmountRules.requireForeign("f", new BigDecimal("1234.500"))).doesNotThrowAnyException();
        assertThatCode(() -> AmountRules.requireForeign("f", new BigDecimal("9999999999.99"))).doesNotThrowAnyException();
        assertThatCode(() -> AmountRules.requireForeign("f", null)).doesNotThrowAnyException();
    }

    @Test
    void foreign_rejectsNegativeTooPreciseAndOverflow() {
        assertThatThrownBy(() -> AmountRules.requireForeign("f", new BigDecimal("-0.01")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("0 이상");
        assertThatThrownBy(() -> AmountRules.requireForeign("f", new BigDecimal("1.001")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("소수 2자리");
        assertThatThrownBy(() -> AmountRules.requireForeign("f", new BigDecimal("10000000000.00")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("범위");
    }
}
