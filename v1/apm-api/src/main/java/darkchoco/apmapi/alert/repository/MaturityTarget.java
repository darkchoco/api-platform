package darkchoco.apmapi.alert.repository;

import java.time.LocalDateTime;

/**
 * 만기 알림 대상인 투자건(investment) 한 건.
 *
 * @param finInstNm 판매처명. fin_inst_nm이 NULL 허용이라 없을 수 있다
 */
public record MaturityTarget(int investId, String productNm, String finInstNm, LocalDateTime lastDueDate) {

    /** 알림 제목: "{상품명} · {금융기관명}". 금융기관명이 없으면 상품명만 쓴다. */
    public String alertTitle() {
        if (finInstNm == null || finInstNm.isBlank()) {
            return productNm;
        }
        return productNm + " · " + finInstNm;
    }
}
