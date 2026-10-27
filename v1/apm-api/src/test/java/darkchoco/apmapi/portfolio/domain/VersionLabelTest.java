package darkchoco.apmapi.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class VersionLabelTest {

    @Test
    void firstOf_formatsYearMonthAsYyMmWithSerialZero() {
        assertThat(VersionLabel.firstOf(YearMonth.of(2026, 10))).hasToString("v2610.0");
        assertThat(VersionLabel.firstOf(YearMonth.of(2027, 1))).hasToString("v2701.0");
    }

    @Test
    void parse_rejectsLabelsOutsideTheRule() {
        assertThatThrownBy(() -> VersionLabel.parse("1.0")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> VersionLabel.parse("v2610")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> VersionLabel.parse("v2610.x")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> VersionLabel.parse("v261.0")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void next_usesMaxSerialOfSamePrefixPlusOne() {
        VersionLabel base = VersionLabel.parse("v2610.0");

        VersionLabel next = base.next(List.of("v2610.0", "v2610.1", "v2609.7", "v2611.0", "manual-label"));

        assertThat(next).hasToString("v2610.2");
    }

    @Test
    void next_keepsBasePrefixEvenWhenNewerMonthExists() {
        VersionLabel base = VersionLabel.parse("v2610.3");

        VersionLabel next = base.next(List.of("v2610.0", "v2610.3", "v2611.0"));

        assertThat(next).hasToString("v2610.4");
    }
}
