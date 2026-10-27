package darkchoco.apmapi.portfolio.service;

import darkchoco.apmapi.common.exception.ConflictException;
import darkchoco.apmapi.common.exception.ResourceNotFoundException;
import darkchoco.apmapi.portfolio.domain.VersionLabel;
import darkchoco.apmapi.portfolio.dto.NextLabelsResponse;
import darkchoco.apmapi.portfolio.dto.PlanDetailDto;
import darkchoco.apmapi.portfolio.dto.TargetResponse;
import darkchoco.apmapi.portfolio.dto.VersionCreatedResponse;
import darkchoco.apmapi.portfolio.dto.VersionDetailResponse;
import darkchoco.apmapi.portfolio.dto.VersionSaveRequest;
import darkchoco.apmapi.portfolio.dto.VersionSummaryResponse;
import darkchoco.apmapi.portfolio.repository.TargetRepository;
import darkchoco.apmapi.portfolio.repository.VersionHeader;
import darkchoco.apmapi.portfolio.repository.VersionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VersionService.class);

    private final VersionRepository versionRepository;
    private final TargetRepository targetRepository;
    private final VersionSaveValidator validator;
    private final Clock clock;

    public VersionService(VersionRepository versionRepository, TargetRepository targetRepository,
                          VersionSaveValidator validator, Clock clock) {
        this.versionRepository = versionRepository;
        this.targetRepository = targetRepository;
        this.validator = validator;
        this.clock = clock;
    }

    // ---- 조회 ----

    /** versionId 내림차순(= 최신순). */
    public List<VersionSummaryResponse> getVersions() {
        return versionRepository.findSummaries();
    }

    public VersionDetailResponse getVersion(int versionId) {
        VersionHeader header = versionRepository.findHeader(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("버전을 찾을 수 없습니다: " + versionId));
        return toDetailResponse(header);
    }

    /** "최신" = version_id 최대값. 과거 버전을 제자리 수정해도 최신이 바뀌지 않는다. */
    public VersionDetailResponse getLatestVersion() {
        int latestId = versionRepository.findLatestVersionId()
                .orElseThrow(() -> new ResourceNotFoundException("저장된 버전이 없습니다"));
        return getVersion(latestId);
    }

    /** 저장 다이얼로그에 보여줄 라벨 미리보기. 표시용이며 저장 시 서버가 같은 규칙으로 다시 계산한다. */
    public NextLabelsResponse getNextLabels(Integer baseVersionId) {
        String thisMonthLabel = thisMonthLabel().toString();
        var thisMonth = new NextLabelsResponse.ThisMonth(thisMonthLabel, !versionRepository.existsByLabel(thisMonthLabel));

        if (baseVersionId == null) {
            return new NextLabelsResponse(thisMonth, null);
        }

        VersionHeader base = findBaseVersion(baseVersionId);
        var fromBase = new NextLabelsResponse.FromBase(nextFromBase(base).toString(), base.versionLabel());
        return new NextLabelsResponse(thisMonth, fromBase);
    }

    // ---- 저장 ----

    /**
     * 새 버전 생성. baseVersionId가 없으면 이번 달 {@code v{YYMM}.0}, 있으면 기준 버전의 라벨에서 n을 올린다.
     * 어느 경우든 본문의 값이 새 버전에 저장된다.
     */
    @Transactional
    public VersionCreatedResponse createVersion(VersionSaveRequest request) {
        validator.validate(request, targetIds());

        String label = request.baseVersionId() == null
                ? labelForNewMonthVersion()
                : nextFromBase(findBaseVersion(request.baseVersionId())).toString();

        int versionId = insertVersion(label, request);
        versionRepository.replacePlanDetails(versionId, request.detail());

        LOGGER.info("Portfolio 버전 생성: versionId={}, label={}, baseVersionId={}",
                versionId, label, request.baseVersionId());
        return new VersionCreatedResponse(versionId, label);
    }

    /** 제자리 수정. 라벨은 바뀌지 않고 upd_date만 갱신된다. */
    @Transactional
    public VersionDetailResponse updateVersion(int versionId, VersionSaveRequest request) {
        validator.validate(request, targetIds());

        versionRepository.findHeader(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("버전을 찾을 수 없습니다: " + versionId));

        versionRepository.update(versionId, request, now());
        versionRepository.replacePlanDetails(versionId, request.detail());

        LOGGER.info("Portfolio 버전 수정: versionId={}", versionId);
        return getVersion(versionId);
    }

    // ---- 내부 ----

    private String labelForNewMonthVersion() {
        String label = thisMonthLabel().toString();
        if (versionRepository.existsByLabel(label)) {
            throw new ConflictException(
                    "이번 달 버전(%s)이 이미 있습니다. '조회 및 업데이트'에서 수정하거나 새 버전으로 저장하세요".formatted(label));
        }
        return label;
    }

    private int insertVersion(String label, VersionSaveRequest request) {
        try {
            return versionRepository.insert(label, request, now());
        } catch (DuplicateKeyException e) {
            // 존재 확인 이후 다른 요청이 같은 라벨을 먼저 저장한 경우 (UNIQUE(version_label) 위반)
            throw new ConflictException("같은 라벨(%s)의 버전이 방금 저장되었습니다. 다시 시도하세요".formatted(label));
        }
    }

    private VersionLabel thisMonthLabel() {
        return VersionLabel.firstOf(YearMonth.now(clock));
    }

    private VersionHeader findBaseVersion(int baseVersionId) {
        return versionRepository.findHeader(baseVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("기준 버전을 찾을 수 없습니다: " + baseVersionId));
    }

    // 월이 바뀌어도 기준 버전의 YYMM 접두사를 유지한다 (v2610.3을 11월에 기준으로 저장하면 v2610.4).
    private VersionLabel nextFromBase(VersionHeader base) {
        VersionLabel baseLabel = VersionLabel.parse(base.versionLabel());
        return baseLabel.next(versionRepository.findLabelsByPrefix(baseLabel.prefix()));
    }

    // 초 미만은 버린다 - DATETIME(0)에 넣을 때 DB가 반올림하면 시각이 1초 앞서 보일 수 있다.
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private Set<Integer> targetIds() {
        return targetRepository.findAll().stream()
                .map(TargetResponse::schemeAcId)
                .collect(Collectors.toSet());
    }

    private VersionDetailResponse toDetailResponse(VersionHeader header) {
        return new VersionDetailResponse(
                header.versionId(), header.versionLabel(),
                header.availKrw(), header.availUsd(), header.availEur(),
                header.planKrw(), header.planUsd(), header.planEur(),
                header.fxUsd(), header.fxEur(), header.fetchDate(), header.insDate(),
                detailForAllTargets(header.versionId()));
    }

    // 대상이 늘어도 화면이 깨지지 않도록 항상 대상 전부를 내려주고, DB에 행이 없는 대상은 0/null로 채운다.
    private List<PlanDetailDto> detailForAllTargets(int versionId) {
        Map<Integer, PlanDetailDto> savedBySchemeAcId = versionRepository.findPlanDetails(versionId).stream()
                .collect(Collectors.toMap(PlanDetailDto::schemeAcId, Function.identity()));

        return targetRepository.findAll().stream()
                .map(target -> savedBySchemeAcId.getOrDefault(target.schemeAcId(), emptyDetail(target.schemeAcId())))
                .map(this::zeroIfRatioMissing)
                .toList();
    }

    private PlanDetailDto emptyDetail(int schemeAcId) {
        return new PlanDetailDto(schemeAcId, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);
    }

    // DB 컬럼이 NULL이면 비율은 0으로 내려준다.
    private PlanDetailDto zeroIfRatioMissing(PlanDetailDto row) {
        return new PlanDetailDto(
                row.schemeAcId(),
                row.krwUsdRatio() == null ? BigDecimal.ZERO : row.krwUsdRatio(),
                row.eurRatio() == null ? BigDecimal.ZERO : row.eurRatio(),
                row.execKrw(), row.execUsd(), row.execEur());
    }
}
