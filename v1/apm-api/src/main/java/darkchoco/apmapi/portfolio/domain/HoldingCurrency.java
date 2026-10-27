package darkchoco.apmapi.portfolio.domain;

/**
 * 종목(보유 건)의 통화. Portfolio가 다루는 통화만 정의한다 - JPY 보유 건은 집계 대상이 아니다.
 * 종목은 통화가 하나이며(설계상 보유 종목 : 통화 = 1 : 1) 종목의 투자계획금액은 그 통화의 컬럼에만 저장된다.
 */
public enum HoldingCurrency {
    KRW,
    USD,
    EUR
}
