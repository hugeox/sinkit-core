package biz.karms.sinkit.rest;

import javax.ejb.Local;
import java.util.HashMap;
import java.util.concurrent.Future;

/**
 * @author Krystof Kolar
 */
@Local
public interface RedisIocProcessor {

    Future<HashMap<String, Integer>> runRedisIocProcessing() throws InterruptedException;
}
