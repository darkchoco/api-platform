package darkchoco.apmapi.portfolio.dto;

import java.math.BigDecimal;

/**
 * 종목 투자계획금액 저장 요청. 통화는 받지 않는다 - 서버가 종목의 보유 통화로 정한다.
 *
 * @param planAmount 종목 통화 기준 금액. null이면 "계획 없음"으로 보고 저장된 계획을 삭제한다
 */
public record ItemPlanRequest(BigDecimal planAmount) {
}
