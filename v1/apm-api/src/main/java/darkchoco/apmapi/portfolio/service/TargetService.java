package darkchoco.apmapi.portfolio.service;

import darkchoco.apmapi.portfolio.dto.TargetResponse;
import darkchoco.apmapi.portfolio.repository.TargetRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TargetService {

    private final TargetRepository targetRepository;

    public TargetService(TargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    /** Portfolio 대상 전체, schemeAcId 오름차순. 화면 표시 순서는 프론트가 정한다. */
    public List<TargetResponse> getTargets() {
        return targetRepository.findAll();
    }
}
