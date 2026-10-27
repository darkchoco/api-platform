package darkchoco.apmapi.portfolio.domain;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import java.math.BigDecimal;

/**
 * 금액 컬럼의 업무 규칙. 원화는 {@code DECIMAL(12)}, 달러·유로 금액과 환율은 {@code DECIMAL(12,2)}에 저장되므로
 * 넘치거나 소수 자릿수가 많은 값을 DB가 조용히 반올림하거나 500으로 터지지 않도록 400으로 거절한다.
 * 모든 메서드는 null을 통과시킨다(값이 없는 것은 호출하는 쪽이 판단).
 */
public final class AmountRules {

    private static final BigDecimal MAX_KRW = new BigDecimal("999999999999");
    private static final BigDecimal MAX_FOREIGN = new BigDecimal("9999999999.99");

    private AmountRules() {
    }

    /** 원화: 0 이상 정수, DECIMAL(12) 범위. */
    public static void requireKrw(String field, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        requireNonNegative(field, amount);
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("%s: 정수만 입력할 수 있습니다".formatted(field));
        }
        requireWithin(field, amount, MAX_KRW);
    }

    /** 달러·유로·환율: 0 이상, 소수 2자리까지, DECIMAL(12,2) 범위. */
    public static void requireForeign(String field, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        requireNonNegative(field, amount);
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("%s: 소수 2자리까지만 입력할 수 있습니다".formatted(field));
        }
        requireWithin(field, amount, MAX_FOREIGN);
    }

    private static void requireNonNegative(String field, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new InvalidRequestException("%s: 0 이상이어야 합니다".formatted(field));
        }
    }

    private static void requireWithin(String field, BigDecimal amount, BigDecimal max) {
        if (amount.compareTo(max) > 0) {
            throw new InvalidRequestException("%s: 허용 범위를 초과했습니다".formatted(field));
        }
    }
}
