package darkchoco.apmapi.portfolio.service;

import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import darkchoco.apmapi.portfolio.domain.TargetRatioCalculator;
import darkchoco.apmapi.portfolio.domain.TargetRatioCalculator.PlanBasis;
import darkchoco.apmapi.portfolio.dto.ItemResponse;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.repository.HoldingRepository;
import darkchoco.apmapi.portfolio.repository.HoldingRow;
import darkchoco.apmapi.portfolio.repository.ItemPlan;
import darkchoco.apmapi.portfolio.repository.ItemPlanRepository;
import darkchoco.apmapi.portfolio.repository.VersionHeader;
import darkchoco.apmapi.portfolio.repository.VersionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 종목별 세부 현황. 종목 행은 저장하지 않고 호출 시점마다 최신 기준월의 보유 건에서 구성한다
 * (같은 종목을 여러 계좌에 가졌다면 합산, 매도·청산된 종목은 자연히 빠진다).
 */
@Service
public class ItemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemService.class);

    private final HoldingRepository holdingRepository;
    private final ItemPlanRepository itemPlanRepository;
    private final VersionRepository versionRepository;

    public ItemService(HoldingRepository holdingRepository, ItemPlanRepository itemPlanRepository,
                       VersionRepository versionRepository) {
        this.holdingRepository = holdingRepository;
        this.itemPlanRepository = itemPlanRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * @param schemeAcId 지정하면 그 대상의 종목만 (없는 대상이면 빈 목록), null이면 전체
     * @return schemeAcId, productNm 오름차순
     */
    public List<ItemResponse> getItems(Integer schemeAcId) {
        Map<Integer, List<HoldingRow>> rowsByProduct = holdingRepository.findHoldings(schemeAcId).stream()
                .collect(Collectors.groupingBy(HoldingRow::productId, LinkedHashMap::new, Collectors.toList()));

        Map<Integer, ItemPlan> planByProduct = itemPlanRepository.findAll().stream()
                .collect(Collectors.toMap(ItemPlan::productId, Function.identity()));
        Optional<LatestPlan> latestPlan = loadLatestPlan();

        return rowsByProduct.values().stream()
                .map(rows -> toItem(rows, planByProduct.get(rows.getFirst().productId()), latestPlan))
                .toList();
    }

    // 종목은 통화가 하나라는 것이 설계 전제다. 한 종목이 둘 이상의 통화로 나오면 데이터 오류이므로
    // 서버 오류로 만들지 않고 로그만 남긴 뒤 첫 통화(알파벳순)를 쓴다. 통화별 시가는 보유분 그대로 합산한다.
    private ItemResponse toItem(List<HoldingRow> rows, ItemPlan plan, Optional<LatestPlan> latestPlan) {
        HoldingRow first = rows.getFirst();
        HoldingCurrency currency = first.currency();
        if (rows.size() > 1) {
            LOGGER.warn("종목의 보유 통화가 둘 이상입니다 - 첫 통화 {}를 사용합니다: productId={}, productNm={}, currencies={}",
                    currency, first.productId(), first.productNm(), rows.stream().map(HoldingRow::currency).toList());
        }

        long currentKrw = rows.stream().mapToLong(HoldingRow::currentKrw).sum();
        BigDecimal currentUsd = rows.stream().map(HoldingRow::currentUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentEur = rows.stream().map(HoldingRow::currentEur).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 계획금액은 종목의 통화 컬럼에서만 읽고 나머지 둘은 null로 내린다.
        Long planKrw = currency == HoldingCurrency.KRW && plan != null ? plan.planKrw() : null;
        BigDecimal planUsd = currency == HoldingCurrency.USD && plan != null ? plan.planUsd() : null;
        BigDecimal planEur = currency == HoldingCurrency.EUR && plan != null ? plan.planEur() : null;

        BigDecimal planAmount = switch (currency) {
            case KRW -> planKrw == null ? null : BigDecimal.valueOf(planKrw);
            case USD -> planUsd;
            case EUR -> planEur;
        };

        return new ItemResponse(first.productId(), first.schemeAcId(), first.productNm(), currency,
                first.symbolAlias(), first.productNmDetail(), planKrw, planUsd, planEur,
                currentKrw, currentUsd, currentEur,
                targetRatio(currency, planAmount, first.schemeAcId(), latestPlan));
    }

    // 최근 버전이 하나도 없으면 비율의 기준이 없으므로 null이다 (404가 아니다).
    private BigDecimal targetRatio(HoldingCurrency currency, BigDecimal planAmount, int schemeAcId,
                                   Optional<LatestPlan> latestPlan) {
        if (latestPlan.isEmpty()) {
            return null;
        }
        PlanDetailDto detail = latestPlan.get().detailBySchemeAcId().get(schemeAcId);
        BigDecimal krwUsdRatio = detail == null ? null : detail.krwUsdRatio();
        BigDecimal eurRatio = detail == null ? null : detail.eurRatio();

        return TargetRatioCalculator.calculate(currency, planAmount, krwUsdRatio, eurRatio, latestPlan.get().basis());
    }

    // "최근" 버전 = version_id 최대값. 환율(fxUsd)도 이 버전에 저장된 값을 쓴다.
    private Optional<LatestPlan> loadLatestPlan() {
        return versionRepository.findLatestVersionId().flatMap(versionId ->
                versionRepository.findHeader(versionId).map(header -> new LatestPlan(
                        toBasis(header),
                        versionRepository.findPlanDetails(versionId).stream()
                                .collect(Collectors.toMap(PlanDetailDto::schemeAcId, Function.identity())))));
    }

    private PlanBasis toBasis(VersionHeader header) {
        return new PlanBasis(
                header.planKrw() == null ? null : BigDecimal.valueOf(header.planKrw()),
                header.planUsd(), header.planEur(), header.fxUsd());
    }

    private record LatestPlan(PlanBasis basis, Map<Integer, PlanDetailDto> detailBySchemeAcId) {
    }
}
