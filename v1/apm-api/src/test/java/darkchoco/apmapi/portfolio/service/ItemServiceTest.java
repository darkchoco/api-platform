package darkchoco.apmapi.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import darkchoco.apmapi.portfolio.domain.HoldingCurrency;
import darkchoco.apmapi.portfolio.dto.ItemResponse;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.repository.HoldingRepository;
import darkchoco.apmapi.portfolio.repository.HoldingRow;
import darkchoco.apmapi.portfolio.repository.ItemPlan;
import darkchoco.apmapi.portfolio.repository.ItemPlanRepository;
import darkchoco.apmapi.portfolio.repository.VersionHeader;
import darkchoco.apmapi.portfolio.repository.VersionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private ItemPlanRepository itemPlanRepository;
    @Mock
    private VersionRepository versionRepository;

    private ItemService service;

    @BeforeEach
    void setUp() {
        service = new ItemService(holdingRepository, itemPlanRepository, versionRepository);
    }

    @Test
    void getItems_keepsRepositoryOrderAndGivesKrwItemItsPlanOnlyInKrwColumn() {
        givenLatestVersion();
        when(holdingRepository.findHoldings(null)).thenReturn(List.of(
                holding(1, 1, "TIGER 200", HoldingCurrency.KRW, 8_500_000L, "0.00", "0.00"),
                holding(2, 5, "VOO", HoldingCurrency.USD, 0L, "5000.00", "0.00")));
        when(itemPlanRepository.findAll()).thenReturn(List.of(new ItemPlan(1, 8_000_000L, null, null)));

        List<ItemResponse> items = service.getItems(null);

        assertThat(items).extracting(ItemResponse::productId).containsExactly(1, 2);

        ItemResponse tiger = items.get(0);
        assertThat(tiger.currency()).isEqualTo(HoldingCurrency.KRW);
        assertThat(tiger.planKrw()).isEqualTo(8_000_000L);
        assertThat(tiger.planUsd()).isNull();
        assertThat(tiger.planEur()).isNull();
        assertThat(tiger.currentKrw()).isEqualTo(8_500_000L);
        assertThat(tiger.targetRatio()).isEqualByComparingTo("0.4617");

        ItemResponse voo = items.get(1);
        assertThat(voo.planKrw()).isNull();
        assertThat(voo.planUsd()).isNull();
        assertThat(voo.targetRatio()).isNull();
    }

    @Test
    void getItems_readsPlanFromTheColumnOfTheItemCurrencyOnly() {
        givenLatestVersion();
        when(holdingRepository.findHoldings(5)).thenReturn(List.of(
                holding(2, 5, "VOO", HoldingCurrency.USD, 0L, "5000.00", "0.00")));
        // 통화가 USD인데 KRW 컬럼에 남은 값은 무시한다
        when(itemPlanRepository.findAll()).thenReturn(List.of(
                new ItemPlan(2, 999L, new BigDecimal("1000.00"), new BigDecimal("77.00"))));

        ItemResponse voo = service.getItems(5).getFirst();

        assertThat(voo.planKrw()).isNull();
        assertThat(voo.planUsd()).isEqualByComparingTo("1000.00");
        assertThat(voo.planEur()).isNull();
        assertThat(voo.targetRatio()).isEqualByComparingTo("0.0779");
    }

    @Test
    void getItems_withoutAnyVersion_hasNullTargetRatioButStillReturnsItems() {
        when(versionRepository.findLatestVersionId()).thenReturn(Optional.empty());
        when(holdingRepository.findHoldings(null)).thenReturn(List.of(
                holding(1, 1, "TIGER 200", HoldingCurrency.KRW, 8_500_000L, "0.00", "0.00")));
        when(itemPlanRepository.findAll()).thenReturn(List.of(new ItemPlan(1, 8_000_000L, null, null)));

        ItemResponse item = service.getItems(null).getFirst();

        assertThat(item.planKrw()).isEqualTo(8_000_000L);
        assertThat(item.targetRatio()).isNull();
    }

    @Test
    void getItems_forTargetMissingFromLatestVersionDetail_hasNullTargetRatio() {
        givenLatestVersion();
        when(holdingRepository.findHoldings(null)).thenReturn(List.of(
                holding(3, 3, "KODEX 골드", HoldingCurrency.KRW, 1_000_000L, "0.00", "0.00")));
        when(itemPlanRepository.findAll()).thenReturn(List.of(new ItemPlan(3, 500_000L, null, null)));

        assertThat(service.getItems(null).getFirst().targetRatio()).isNull();
    }

    @Test
    void getItems_mergesRowsOfAProductWithMixedCurrenciesUsingTheFirstCurrency() {
        givenLatestVersion();
        // 데이터 오류: 같은 종목이 EUR와 KRW로 나뉘어 있다. 500이 아니라 첫 통화(EUR)를 쓰고 시가는 그대로 합산한다
        when(holdingRepository.findHoldings(null)).thenReturn(List.of(
                holding(9, 4, "혼합 종목", HoldingCurrency.EUR, 0L, "0.00", "300.00"),
                holding(9, 4, "혼합 종목", HoldingCurrency.KRW, 2_000_000L, "0.00", "0.00")));
        when(itemPlanRepository.findAll()).thenReturn(List.of());

        List<ItemResponse> items = service.getItems(null);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().currency()).isEqualTo(HoldingCurrency.EUR);
        assertThat(items.getFirst().currentKrw()).isEqualTo(2_000_000L);
        assertThat(items.getFirst().currentEur()).isEqualByComparingTo("300.00");
    }

    @Test
    void getItems_withNoHoldings_returnsEmptyList() {
        when(holdingRepository.findHoldings(99)).thenReturn(List.of());
        when(itemPlanRepository.findAll()).thenReturn(List.of());
        when(versionRepository.findLatestVersionId()).thenReturn(Optional.empty());

        assertThat(service.getItems(99)).isEmpty();
    }

    // 문서 4장 예시 버전: planKrw 45,000,000 / planUsd 18,000 / fxUsd 1,350.5, 대상 1과 5의 krwUsdRatio 0.25
    private void givenLatestVersion() {
        when(versionRepository.findLatestVersionId()).thenReturn(Optional.of(3));
        when(versionRepository.findHeader(3)).thenReturn(Optional.of(new VersionHeader(
                3, "v2608.0", null, null, null,
                45_000_000L, new BigDecimal("18000.00"), new BigDecimal("4500.00"),
                new BigDecimal("1350.50"), new BigDecimal("1470.20"), null,
                LocalDateTime.of(2026, 8, 1, 0, 0), null)));
        when(versionRepository.findPlanDetails(3)).thenReturn(List.of(
                new PlanDetailDto(1, new BigDecimal("0.25"), BigDecimal.ZERO, null, null, null),
                new PlanDetailDto(5, new BigDecimal("0.25"), BigDecimal.ZERO, null, null, null)));
    }

    private HoldingRow holding(int productId, int schemeAcId, String productNm, HoldingCurrency currency,
                               long currentKrw, String currentUsd, String currentEur) {
        return new HoldingRow(productId, schemeAcId, productNm, "SYM" + productId, productNm + " 상세", currency,
                currentKrw, new BigDecimal(currentUsd), new BigDecimal(currentEur));
    }
}
