package biz.karms.sinkit.rest;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.ScheduleExpression;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * @author Krystof Kolar
 */

@Singleton
@LocalBean
@Startup
public class RedisSchedulerEJB {


    private static final boolean SINKIT_REDIS_POPPER_ENABLE = (System.getenv().containsKey(
            "SINKIT_REDIS_POPPER_ENABLE"))
            && Boolean.parseBoolean(System.getenv("SINKIT_REDIS_POPPER_ENABLE"));
    @EJB
    RedisIocProcessor redisIocProcessor;
    @Inject
    private Logger log;
    @Resource
    private TimerService timerService;
    private Future<HashMap<String, Integer>> future = null;

    @PostConstruct
    private void initialize() {
        if (SINKIT_REDIS_POPPER_ENABLE) {
            Integer SINKIT_REDIS_INTERVAL_SECONDS = new Integer(5);
            if (System.getenv().containsKey("SINKIT_REDIS_INTERVAL_SECONDS")) {
                SINKIT_REDIS_INTERVAL_SECONDS = new Integer(System.getenv("SINKIT_REDIS_INTERVAL_SECONDS"));
            }
            timerService.createCalendarTimer(
                    new ScheduleExpression().hour("*").minute("*").second("*/" + SINKIT_REDIS_INTERVAL_SECONDS),
                    new TimerConfig("RedisPopper", false));
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Stop all RedisPopper timers");
        for (Timer timer : timerService.getTimers()) {
            log.fine("Stop RedisPopper timer: " + timer.getInfo());
            timer.cancel();
        }
    }

    @Timeout
    public void scheduler(Timer timer) throws InterruptedException, ExecutionException {
        log.severe("Redis Popper timer info: " + timer.getInfo());
        if (future == null) {
            log.fine("Starting redis processing.");
            future = this.redisIocProcessor.runRedisIocProcessing();
        } else if (future.isDone()) {
            // can analyze results of operation
            log.fine("Finished Redis processing run, ioc stats: " + future.get().toString());
            log.fine("Starting another Redis processing run.");
            future = this.redisIocProcessor.runRedisIocProcessing();
        } else {
            log.fine("Redis data processing still running");
        }
    }
}
