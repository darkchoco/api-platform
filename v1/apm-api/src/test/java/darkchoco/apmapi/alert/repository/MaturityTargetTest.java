package darkchoco.apmapi.alert.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MaturityTargetTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2025, 12, 16, 0, 0);

    @Test
    void alertTitle_joinsProductAndFinancialInstitution() {
        MaturityTarget target = new MaturityTarget(127, "발행어음 만기형_개인(365)", "미래에셋증권", DUE);

        assertThat(target.alertTitle()).isEqualTo("발행어음 만기형_개인(365) · 미래에셋증권");
    }

    @Test
    void alertTitle_usesProductOnlyWhenFinancialInstitutionNameIsMissing() {
        assertThat(new MaturityTarget(1, "정기예금", null, DUE).alertTitle()).isEqualTo("정기예금");
        assertThat(new MaturityTarget(1, "정기예금", " ", DUE).alertTitle()).isEqualTo("정기예금");
    }
}
