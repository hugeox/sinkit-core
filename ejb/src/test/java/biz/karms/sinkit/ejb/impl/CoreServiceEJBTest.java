package biz.karms.sinkit.ejb.impl;

import biz.karms.sinkit.ejb.ArchiveService;
import biz.karms.sinkit.ejb.BlacklistCacheService;
import biz.karms.sinkit.ejb.WhitelistCacheService;
import biz.karms.sinkit.ejb.cache.pojo.WhitelistedRecord;
import biz.karms.sinkit.ioc.IoCFeed;
import biz.karms.sinkit.ioc.IoCRecord;
import biz.karms.sinkit.ioc.IoCSource;
import biz.karms.sinkit.ioc.IoCSourceId;
import biz.karms.sinkit.ioc.IoCSourceIdType;

import java.util.ArrayList;
import java.util.Calendar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Tomas Kozel
 */
@RunWith(MockitoJUnitRunner.class)
public class CoreServiceEJBTest {

    @Mock
    private WhitelistCacheService whitelistCacheService;

    @Mock
    private ArchiveService archiveService;

    @Mock
    private BlacklistCacheService blacklistCacheService;

    @InjectMocks
    private CoreServiceEJB coreService;

    @Test
    public void processExistingCompletedNoUpdateTest() throws Exception {
        Calendar expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.DAY_OF_MONTH, 1);
        WhitelistedRecord white = createWhite("whalebone.io", "whalebone", expiresAt, true);
        IoCRecord ioc = getIoCForWhitelist(null, "whalebone.io", "whalebone", true);
        when(whitelistCacheService.get("whalebone.io")).thenReturn(white);

        assertTrue(coreService.processWhitelistIoCRecord(ioc));

        verify(whitelistCacheService).get("whalebone.io");
        verifyNoMoreInteractions(whitelistCacheService);
        verifyZeroInteractions(archiveService);
        verifyZeroInteractions(blacklistCacheService);
    }

    @Test
    public void processExistingNotCompletedNoUpdateTest() throws Exception {
        Calendar expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.DAY_OF_MONTH, 1);
        WhitelistedRecord white = createWhite("whalebone.io", "whalebone", expiresAt, false);
        IoCRecord ioc = getIoCForWhitelist(null, "whalebone.io", "whalebone", true);
        IoCRecord iocToBeWhite1 = getIoCForWhitelist(null, "whalebone.io", "someFeed", true);
        IoCRecord iocToBeWhite2 = getIoCForWhitelist(null, "greate.whaelobne.io", "someOtherFeed", true);
        ArrayList<IoCRecord> toBeWhiteListed = new ArrayList<>();
        toBeWhiteListed.add(iocToBeWhite1);
        toBeWhiteListed.add(iocToBeWhite2);

        when(whitelistCacheService.get("whalebone.io")).thenReturn(white);
        when(archiveService.findIoCsForWhitelisting("whalebone.io")).thenReturn(toBeWhiteListed);
        when(blacklistCacheService.removeWholeObjectFromCache(any(IoCRecord.class))).thenReturn(true);
        when(whitelistCacheService.setCompleted(white)).thenReturn(white);

        assertTrue(coreService.processWhitelistIoCRecord(ioc));

        verify(whitelistCacheService).get("whalebone.io");
        verify(archiveService).findIoCsForWhitelisting("whalebone.io");
        verify(archiveService).setRecordWhitelisted(iocToBeWhite1, "whalebone");
        verify(archiveService).setRecordWhitelisted(iocToBeWhite2, "whalebone");
        verify(blacklistCacheService).removeWholeObjectFromCache(iocToBeWhite1);
        verify(blacklistCacheService).removeWholeObjectFromCache(iocToBeWhite2);
        verify(whitelistCacheService).setCompleted(white);
        verifyNoMoreInteractions(whitelistCacheService);
        verifyNoMoreInteractions(blacklistCacheService);
        verifyNoMoreInteractions(archiveService);
    }

    @Test
    public void processExistingCompletedUpdateTest() throws Exception {
        coreService.setWhitelistValidSeconds(1 * 60 * 60); // 1 hour
        Calendar expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.MINUTE, 30);
        WhitelistedRecord white = createWhite("whalebone.io", "whalebone", expiresAt, true);
        Calendar expiresAt2 = Calendar.getInstance();
        expiresAt2.add(Calendar.HOUR, 1);
        WhitelistedRecord white2 = createWhite("whalebone.io", "whalebone2", expiresAt2, true);
        IoCRecord ioc = getIoCForWhitelist(null, "whalebone.io", "whalebone2", true);
        when(whitelistCacheService.get("whalebone.io")).thenReturn(white);
        when(whitelistCacheService.put(any(IoCRecord.class), eq(true))).thenReturn(white2);

        assertTrue(coreService.processWhitelistIoCRecord(ioc));

        verify(whitelistCacheService).get("whalebone.io");
        verify(whitelistCacheService).put(ioc, true);
        verifyNoMoreInteractions(whitelistCacheService);
        verifyZeroInteractions(blacklistCacheService);
        verifyZeroInteractions(archiveService);
    }

    @Test
    public void processExistingNotCompletedUpdateTest() throws Exception {
        coreService.setWhitelistValidSeconds(1 * 60 * 60); // 1 hour
        Calendar expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.MINUTE, 30);
        WhitelistedRecord white = createWhite("whalebone.io", "whalebone", expiresAt, false);
        Calendar expiresAt2 = Calendar.getInstance();
        expiresAt2.add(Calendar.HOUR, 1);
        WhitelistedRecord white2 = createWhite("whalebone.io", "whalebone2", expiresAt2, true);
        IoCRecord ioc = getIoCForWhitelist(null, "whalebone.io", "whalebone2", true);
        IoCRecord iocToBeWhite1 = getIoCForWhitelist(null, "whalebone.io", "someFeed", true);
        IoCRecord iocToBeWhite2 = getIoCForWhitelist(null, "greate.whaelobne.io", "someOtherFeed", true);
        ArrayList<IoCRecord> toBeWhiteListed = new ArrayList<>();
        toBeWhiteListed.add(iocToBeWhite1);
        toBeWhiteListed.add(iocToBeWhite2);

        when(whitelistCacheService.get("whalebone.io")).thenReturn(white);
        when(whitelistCacheService.put(any(IoCRecord.class), eq(false))).thenReturn(white2);
        when(archiveService.findIoCsForWhitelisting("whalebone.io")).thenReturn(toBeWhiteListed);
        when(blacklistCacheService.removeWholeObjectFromCache(any(IoCRecord.class))).thenReturn(true);
        when(whitelistCacheService.setCompleted(white2)).thenReturn(white2);

        assertTrue(coreService.processWhitelistIoCRecord(ioc));

        verify(whitelistCacheService).get("whalebone.io");
        verify(whitelistCacheService).put(ioc, false);
        verify(archiveService).findIoCsForWhitelisting("whalebone.io");
        verify(archiveService).setRecordWhitelisted(iocToBeWhite1, "whalebone2");
        verify(archiveService).setRecordWhitelisted(iocToBeWhite2, "whalebone2");
        verify(blacklistCacheService).removeWholeObjectFromCache(iocToBeWhite1);
        verify(blacklistCacheService).removeWholeObjectFromCache(iocToBeWhite2);
        verify(whitelistCacheService).setCompleted(white2);
        verifyNoMoreInteractions(whitelistCacheService);
        verifyNoMoreInteractions(blacklistCacheService);
        verifyNoMoreInteractions(archiveService);
    }

    @Test
    public void processNotExistingTest() throws Exception {
        coreService.setWhitelistValidSeconds(1 * 60 * 60); // 1 hour
        Calendar expiresAt = Calendar.getInstance();
        expiresAt.add(Calendar.HOUR, 1);
        WhitelistedRecord white = createWhite("whalebone.io", "whalebone", expiresAt, false);
        IoCRecord ioc = getIoCForWhitelist(null, "whalebone.io", "whalebone", true);
        IoCRecord iocToBeWhite1 = getIoCForWhitelist(null, "whalebone.io", "someFeed", true);
        IoCRecord iocToBeWhite2 = getIoCForWhitelist(null, "greate.whaelobne.io", "someOtherFeed", true);
        ArrayList<IoCRecord> toBeWhiteListed = new ArrayList<>();
        toBeWhiteListed.add(iocToBeWhite1);
        toBeWhiteListed.add(iocToBeWhite2);

        when(whitelistCacheService.get("whalebone.io")).thenReturn(null);
        when(whitelistCacheService.put(any(IoCRecord.class), eq(false))).thenReturn(white);
        when(archiveService.findIoCsForWhitelisting("whalebone.io")).thenReturn(toBeWhiteListed);
        when(blacklistCacheService.removeWholeObjectFromCache(any(IoCRecord.class))).thenReturn(true);
        when(whitelistCacheService.setCompleted(white)).thenReturn(white);

        assertTrue(coreService.processWhitelistIoCRecord(ioc));

        verify(whitelistCacheService).get("whalebone.io");
        verify(whitelistCacheService).put(ioc, false);
        verify(archiveService).findIoCsForWhitelisting("whalebone.io");
        verify(archiveService).setRecordWhitelisted(iocToBeWhite1, "whalebone");
        verify(archiveService).setRecordWhitelisted(iocToBeWhite2, "whalebone");
        verify(blacklistCacheService).removeWholeObjectFromCache(iocToBeWhite1);
        verify(blacklistCacheService).removeWholeObjectFromCache(iocToBeWhite2);
        verify(whitelistCacheService).setCompleted(white);
        verifyNoMoreInteractions(whitelistCacheService);
        verifyNoMoreInteractions(blacklistCacheService);
        verifyNoMoreInteractions(archiveService);
    }

    private WhitelistedRecord createWhite(String rawId, String sourceName, Calendar expiresAt, boolean completed) {
        WhitelistedRecord white = new WhitelistedRecord();
        white.setCompleted(completed);
        white.setRawId(rawId);
        white.setExpiresAt(expiresAt);
        white.setSourceName(sourceName);
        return white;
    }

    public IoCRecord getIoCForWhitelist(String ip, String fqdn, String sourceName, boolean withId) {
        IoCRecord ioc = new IoCRecord();
        ioc.setSource(new IoCSource());
        ioc.getSource().setIp(ip);
        ioc.getSource().setFqdn(fqdn);
        ioc.setFeed(new IoCFeed());
        ioc.getFeed().setName(sourceName);
        if (withId) {
            ioc.getSource().setId(new IoCSourceId());
            if (fqdn != null) {
                ioc.getSource().getId().setValue(fqdn);
                ioc.getSource().getId().setType(IoCSourceIdType.FQDN);
            } else if (ip != null) {
                ioc.getSource().getId().setValue(ip);
                ioc.getSource().getId().setType(IoCSourceIdType.IP);
            }
        }
        return ioc;
    }

    @Test
    public void test() {
        System.out.println(System.getProperty("java.io.tmpdir"));
        System.out.println(Math.ceil((1001 - 1000) / 1000.00));
    }
}
