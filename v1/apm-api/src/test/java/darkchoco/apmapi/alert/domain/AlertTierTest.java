package darkchoco.apmapi.alert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlertTierTest {

    @ParameterizedTest
    @CsvSource({
            "-9000, OVERDUE",
            "-1, OVERDUE",
            "0, URGENT",
            "14, URGENT",
            "15, MILD",
            "30, MILD"
    })
    void forMaturity_mapsDaysLeftToTier(long daysLeft, AlertTier expected) {
        assertThat(AlertTier.forMaturity(daysLeft)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"31", "365"})
    void forMaturity_noAlertWhenMoreThan30DaysLeft(long daysLeft) {
        assertThat(AlertTier.forMaturity(daysLeft)).isEmpty();
    }
}
