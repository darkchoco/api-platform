package darkchoco.apmapi.portfolio.service;

import darkchoco.apmapi.common.exception.InvalidRequestException;
import darkchoco.apmapi.common.exception.ResourceNotFoundException;
import darkchoco.apmapi.portfolio.domain.AmountRules;
import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import darkchoco.apmapi.portfolio.domain.TargetRatioCalculator;
import darkchoco.apmapi.portfolio.domain.TargetRatioCalculator.PlanBasis;
import darkchoco.apmapi.portfolio.dto.ItemResponse;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.repository.HoldingRepository;
import darkchoco.apmapi.portfolio.repository.HoldingRow;
import darkchoco.apmapi.portfolio.repository.ItemPlan;
import darkchoco.apmapi.portfolio.repository.ItemPlanRepository;
import darkchoco.apmapi.portfolio.repository.ProductRepository;
import darkchoco.apmapi.portfolio.repository.VersionHeader;
import darkchoco.apmapi.portfolio.repository.VersionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProductRepository productRepository;
    private final Clock clock;

    public ItemService(HoldingRepository holdingRepository, ItemPlanRepository itemPlanRepository,
                       VersionRepository versionRepository, ProductRepository productRepository, Clock clock) {
        this.holdingRepository = holdingRepository;
        this.itemPlanRepository = itemPlanRepository;
        this.versionRepository = versionRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    /**
     * @param schemeAcId 지정하면 그 대상의 종목만 (없는 대상이면 빈 목록), null이면 전체
     * @return schemeAcId, productNm 오름차순
     */
    public List<ItemResponse> getItems(Integer schemeAcId) {
        return buildItems(holdingRepository.findHoldings(schemeAcId));
    }

    /**
     * 종목의 투자계획금액을 저장하고 갱신된 종목 1건을 돌려준다 (targetRatio 재계산 포함).
     * 통화는 클라이언트가 정하지 않고 종목의 보유 통화로 서버가 정하며, 그 통화의 컬럼에만 저장한다.
     *
     * @param planAmount 종목 통화 기준 금액. null이면 "계획 없음"으로 보고 행을 삭제한다
     */
    @Transactional
    public ItemResponse updatePlan(int productId, BigDecimal planAmount) {
        if (!productRepository.exists(productId)) {
            throw new ResourceNotFoundException("종목을 찾을 수 없습니다: " + productId);
        }

        // 현재 보유 중이 아닌 종목(또는 Portfolio 대상이 아닌 종목)은 통화를 알 수 없어 저장하지 않는다.
        List<HoldingRow> rows = holdingRepository.findHoldingsOfProduct(productId);
        if (rows.isEmpty()) {
            throw new InvalidRequestException(
                    "현재 보유 중인 Portfolio 종목이 아니라 투자계획금액을 저장할 수 없습니다: " + productId);
        }

        // 통화가 둘 이상으로 나오는 데이터 오류 종목은 조회(toItem)와 같이 첫 통화를 따른다.
        HoldingCurrency currency = rows.getFirst().currency();
        if (planAmount == null) {
            itemPlanRepository.delete(productId);
            LOGGER.info("종목 투자계획금액 삭제: productId={}", productId);
        } else {
            requireValidAmount(currency, planAmount);
            itemPlanRepository.upsert(productId, currency, planAmount, now());
            LOGGER.info("종목 투자계획금액 저장: productId={}, currency={}, planAmount={}", productId, currency, planAmount);
        }

        return buildItems(rows).getFirst();
    }

    private void requireValidAmount(HoldingCurrency currency, BigDecimal planAmount) {
        switch (currency) {
            case KRW -> AmountRules.requireKrw("planAmount", planAmount);
            case USD, EUR -> AmountRules.requireForeign("planAmount", planAmount);
        }
    }

    // 초 미만은 버린다 - DATETIME에 넣을 때 DB가 반올림하는 것을 피한다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private List<ItemResponse> buildItems(List<HoldingRow> holdings) {
        Map<Integer, List<HoldingRow>> rowsByProduct = holdings.stream()
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
