package biz.karms.sinkit.tests.redis;


import biz.karms.sinkit.ejb.ArchiveService;
import biz.karms.sinkit.ejb.BlacklistCacheService;
import biz.karms.sinkit.ejb.CoreService;
import biz.karms.sinkit.ejb.WebApi;
import biz.karms.sinkit.ioc.IoCRecord;
import biz.karms.sinkit.tests.util.FileUtils;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.testng.annotations.Test;
import redis.clients.jedis.Jedis;

import javax.ejb.EJB;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Michal Karm Babacek
 */
public class RedisIntegrationTest extends Arquillian {

    private static final String REDIS_QUEUE = System.getenv("SINKIT_REDIS_QUEUE");
    private static final String REDIS_PROCESSING_QUEUE = System.getenv("SINKIT_REDIS_PROCESSING_QUEUE");

    @EJB
    BlacklistCacheService blacklistCacheService;

    @EJB
    ArchiveService archiveService;

    @EJB
    CoreService coreService;

    @EJB
    WebApi webApi;

    @Test(enabled = true, dataProvider = Arquillian.ARQUILLIAN_DATA_PROVIDER, priority = 401)
    @OperateOnDeployment("ear")
    @RunAsClient
    public void redisTest(@ArquillianResource URL context) throws Exception {

        //setup iocs
        Jedis jedis = new Jedis(System.getenv("SINKIT_REDIS_HOST"), new Integer(System.getenv("SINKIT_REDIS_PORT")));
        String iocNew1 = FileUtils.readFileIntoString("iocRedisNew1.json");
        String iocNew2 = FileUtils.readFileIntoString("iocRedisNew2.json");
        String iocOld = FileUtils.readFileIntoString("iocRedisOld.json");
        Calendar c = Calendar.getInstance();
        Date now = c.getTime();
        c.add(Calendar.HOUR, -new Integer(System.getenv("SINKIT_IOC_ACTIVE_HOURS")));
        Date tooOld = c.getTime();
        c.add(Calendar.HOUR, 1);
        iocNew1 = iocNew1.replace("TIME_OBSERVATION", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(now));
        iocNew2 = iocNew2.replace("TIME_OBSERVATION", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(now));
        iocOld = iocOld.replace("TIME_OBSERVATION", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(tooOld));

        //send iocs to redis
        jedis.lpush(REDIS_QUEUE, iocNew1, iocNew2, iocOld);

        //wait a second for core to process iocs
        Thread.sleep(3100);

        //check redis
        assertEquals(jedis.llen(REDIS_QUEUE), new Long(0));
        //not even tooOld should stay in redis, as it is too old and we want to throw it away
        assertEquals(jedis.llen(REDIS_PROCESSING_QUEUE), new Long(0));


        //check cache, can be run as client;
        //iocOld shouldn't be in cache - too old

        checkCache("redisTest", "phishing", "fishingredis.ru", context);
        checkCache("redisTest", "phishing", "otherphishingredis.ru", context);
        checkNotInCache("redisTest", "phishing", "phishingtoooldredis.ru", context);
        jedis.close();


    }


    //Can't be run as client!
    @Test(enabled = true, dataProvider = Arquillian.ARQUILLIAN_DATA_PROVIDER, priority = 402)
    public void RedisCheckElasticTest() throws Exception {
        //check elastic
        checkElastic("redisTest", "phishing", "fishingredis.ru");
        checkElastic("redisTest", "phishing", "otherphishingredis.ru");
        checkNotInElastic("redisTest", "phishing", "phishingtoooldredis.ru");

    }


    //Assumes time.deactivated is null for the ioc -
    public void checkElastic(String feed, String type, String fqdn) throws Exception {

        final StringBuilder idString = new StringBuilder();
        idString.append(feed);
        idString.append(type);
        idString.append(fqdn);
        String iocId = DigestUtils.md5Hex(idString.toString());

        IoCRecord ioc = archiveService.getActiveIoCRecordById(iocId);
        assertNotNull(ioc, "Excpecting IoC, but got null with fqdn: " + fqdn + ", type: " + type + ", feed: " + feed);
        assertEquals(ioc.getFeed().getName(), feed, "Expected feed.name: " + feed + ", but got: " + ioc.getFeed().getName());
        assertEquals(ioc.getSource().getId().getValue(), fqdn, "Expected source.id.value: " + fqdn + ", but got: " + ioc.getSource().getId().getValue());
        assertEquals(ioc.getClassification().getType(), type, "Expected classification.type: " + type + ", but got: " + ioc.getClassification().getType());
        assertNotNull(ioc.getSeen().getFirst(), "Expected seen.first but got null");
        assertNotNull(ioc.getSeen().getLast(), "Expected seen.last but got null");
        Calendar c = Calendar.getInstance();
        c.add(Calendar.HOUR, -coreService.getIocActiveHours());
        assertTrue(ioc.getSeen().getLast().after(c.getTime()), "Expected seen.last is not older than " + coreService.getIocActiveHours() + " hours, but got " + ioc.getSeen().getLast());
        assertTrue(ioc.getActive(), "Expected ioc to be active, but got active: false");
        assertNotNull(ioc.getTime().getObservation(), "Expecting time.observation, but got null");
    }

    public void checkNotInElastic(String feed, String type, String fqdn) throws Exception {

        final StringBuilder idString = new StringBuilder();
        idString.append(feed);
        idString.append(type);
        idString.append(fqdn);
        String iocId = DigestUtils.md5Hex(idString.toString());

        IoCRecord ioc = archiveService.getActiveIoCRecordById(iocId);
        assertNull(ioc);
    }

    public void checkCache(String feed, String type, String fqdn, URL context) throws Exception {
        final StringBuilder idString = new StringBuilder();
        idString.append(feed);
        idString.append(type);
        idString.append(fqdn);
        String iocId = DigestUtils.md5Hex(idString.toString());

        String fqdnmd5 = DigestUtils.md5Hex(fqdn);

        WebClient webClient = new WebClient();
        WebRequest requestSettings = new WebRequest(new URL(context + "rest/blacklist/record/" + fqdn), HttpMethod.GET);
        requestSettings.setAdditionalHeader("Content-Type", "application/json");
        Page page = webClient.getPage(requestSettings);
        assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
        String responseBody = page.getWebResponse().getContentAsString();
        String expected = "\"black_listed_domain_or_i_p\":\"" + fqdnmd5 + "\"";  // md5 of phishing.ru
        assertTrue(responseBody.contains(expected), "IoC response should have contained " + expected + ", but got:" + responseBody);
        expected = "\"sources\":{\"" + feed + "\":{\"left\":\"" + type + "\",\"right\":\"" + iocId;
        assertTrue(responseBody.contains(expected), "IoC should have contained " + expected + ", but got: " + responseBody);
    }

    public void checkNotInCache(String feed, String type, String fqdn, URL context) throws Exception {
        String fqdnmd5 = DigestUtils.md5Hex(fqdn);

        WebClient webClient = new WebClient();
        WebRequest requestSettings = new WebRequest(new URL(context + "rest/blacklist/record/" + fqdn), HttpMethod.GET);
        requestSettings.setAdditionalHeader("Content-Type", "application/json");
        Page page = webClient.getPage(requestSettings);
        assertEquals(HttpURLConnection.HTTP_OK, page.getWebResponse().getStatusCode());
        String responseBody = page.getWebResponse().getContentAsString();

        assertEquals(responseBody, "null");
    }
}
