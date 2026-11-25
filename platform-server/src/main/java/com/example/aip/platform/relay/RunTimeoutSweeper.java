package com.example.aip.platform.relay;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.example.aip.platform.config.PlatformProperties;
import com.example.aip.platform.domain.Run;
import com.example.aip.platform.domain.RunStatus;
import com.example.aip.platform.repo.RunRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时兜底：拆分架构下事件经 Redis Stream 回流，若运行面崩溃或事件丢失，
 * run 会永久停留在 RUNNING。此任务每 60s 将创建超过阈值仍 RUNNING 的 run 置 FAILED，
 * 保证控制面 DB 状态最终收敛（这是"以控制面 DB 为准"这一容错点的兜底路径）。
 */
@Component
public class RunTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(RunTimeoutSweeper.class);

    private final RunRepository runRepo;
    private final PlatformProperties props;

    public RunTimeoutSweeper(RunRepository runRepo, PlatformProperties props) {
        this.runRepo = runRepo;
        this.props = props;
    }

    @Scheduled(fixedDelay = 60_000L)
    public void sweep() {
        Instant cutoff = Instant.now().minus(props.getRunTimeoutMinutes(), ChronoUnit.MINUTES);
        List<Run> stale = runRepo.findByStatusAndCreatedAtBefore(RunStatus.RUNNING, cutoff);
        for (Run run : stale) {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
            log.info("run {} marked FAILED by timeout sweep", run.getId());
        }
    }
}
