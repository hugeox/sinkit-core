package biz.karms.sinkit.rest;

import biz.karms.sinkit.exception.IoCValidationException;
import com.google.gson.JsonSyntaxException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PreDestroy;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Singleton;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * @author Krystof Kolar
 */
@Singleton
public class RedisIocProcessorEJB implements RedisIocProcessor {

    private static final String QUEUE = System.getenv("SINKIT_REDIS_QUEUE");
    private static final String PROCESSING_QUEUE = System.getenv("SINKIT_REDIS_PROCESSING_QUEUE");

    @Inject
    private Logger log;

    @Inject
    SinkitService sinkitService;

    JedisPool pool = new JedisPool(new JedisPoolConfig(), System.getenv("SINKIT_REDIS_HOST"), new Integer(System.getenv("SINKIT_REDIS_PORT")));

    @Asynchronous
    @Override
    public Future<HashMap<String, Integer>> runRedisIocProcessing() throws InterruptedException {
        return new AsyncResult<>(processIocsFromRedis());

    }

    private HashMap<String, Integer> processIocsFromRedis() throws InterruptedException {
        Integer discarded_counter = 0;
        Integer failed_counter = 0;
        Integer processed_counter = 0;
        try (Jedis jedis = pool.getResource()) {
            while (jedis.llen(QUEUE) > 0) {
                String ioc = jedis.rpoplpush(QUEUE, PROCESSING_QUEUE);
                try {
                    sinkitService.processIoCRecord(ioc);
                    jedis.lrem(PROCESSING_QUEUE, 1, ioc);
                    processed_counter++;
                } catch (IoCValidationException | JsonSyntaxException ex) {
                    log.severe("Processing IoC went wrong: " + ex.getMessage());
                    //Validation error. We don't want to try processing ioc anymore
                    jedis.lrem(PROCESSING_QUEUE, 1, ioc);
                    discarded_counter++;
                    //TODO: Do something about it (maybe save to some db for manual inspection)
                } catch (Exception ex) {
                    //Some other error: do not remove ioc from processing queue
                    log.severe("Processing IoC went wrong: " + ex.getMessage());
                    failed_counter++;
                }
            }
            //Move all entries from PROCESSING_QUEUE back to QUEUE (ordering is of no importance here)
            while (jedis.llen(PROCESSING_QUEUE) > 0) {
                jedis.rpoplpush(PROCESSING_QUEUE, QUEUE);
            }
        }
        HashMap<String, Integer> stats = new HashMap<>(3);
        stats.put("failed", failed_counter);
        stats.put("discarded", discarded_counter);
        stats.put("processed", processed_counter);
        return stats;

    }

    @PreDestroy
    public void shutdown() {
        log.info("Close Redis pool");
        pool.close();
    }
}
