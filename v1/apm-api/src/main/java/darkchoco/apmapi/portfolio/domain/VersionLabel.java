package darkchoco.apmapi.portfolio.domain;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Portfolio 버전 라벨 {@code v{YYMM}.{n}} (예: {@code v2610.0}).
 *
 * @param yearMonth 라벨의 YYMM 부분 (예: "2610")
 * @param serial    같은 YYMM 안에서의 일련번호 n
 */
public record VersionLabel(String yearMonth, int serial) {

    private static final Pattern FORMAT = Pattern.compile("^v(\\d{4})\\.(\\d+)$");

    /** 해당 월의 첫 버전 {@code v{YYMM}.0}. */
    public static VersionLabel firstOf(YearMonth month) {
        String yymm = "%02d%02d".formatted(month.getYear() % 100, month.getMonthValue());
        return new VersionLabel(yymm, 0);
    }

    /** 형식이 맞지 않으면(과거 수동 데이터 등) 400으로 거절한다. */
    public static VersionLabel parse(String label) {
        return tryParse(label).orElseThrow(() ->
                new InvalidRequestException("버전 라벨 형식이 올바르지 않아 기준으로 쓸 수 없습니다: " + label));
    }

    /** 형식이 맞지 않으면 빈 값. 같은 접두사 후보를 걸러낼 때 쓴다. */
    public static Optional<VersionLabel> tryParse(String label) {
        Matcher matcher = FORMAT.matcher(label);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new VersionLabel(matcher.group(1), Integer.parseInt(matcher.group(2))));
    }

    /** 같은 YYMM을 가진 기존 라벨 중 최대 n + 1. 기준 라벨의 YYMM을 유지한다. */
    public VersionLabel next(Collection<String> existingLabels) {
        int maxExisting = existingLabels.stream()
                .flatMap(label -> tryParse(label).stream())
                .filter(parsed -> parsed.yearMonth.equals(yearMonth))
                .mapToInt(VersionLabel::serial)
                .max()
                .orElse(0);

        return new VersionLabel(yearMonth, Math.max(maxExisting, serial) + 1);
    }

    /** DB LIKE 조회용 접두사 (예: "v2610."). */
    public String prefix() {
        return "v" + yearMonth + ".";
    }

    @Override
    public String toString() {
        return prefix() + serial;
    }
}
