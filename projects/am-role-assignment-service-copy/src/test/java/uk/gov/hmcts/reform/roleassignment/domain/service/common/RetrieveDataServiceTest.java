package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.roleassignment.feignclients.DataStoreApi;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class RetrieveDataServiceTest {


    private final DataStoreApi dataStoreApi = mock(DataStoreApi.class);

    private CacheManager cacheManager = mock(CacheManager.class);

    private CaffeineCache caffeineCacheMock = mock(CaffeineCache.class);

    private final CaffeineCache caffeineCache = mock(CaffeineCache.class);

    @Mock
    Cache<Object, Object> nativeCache;

    private com.github.benmanes.caffeine.cache.Cache cache = mock(com.github.benmanes.caffeine.cache.Cache.class);

    RetrieveDataService sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        sut = new RetrieveDataService(dataStoreApi, cacheManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCaseById() {

        org.springframework.test.util.ReflectionTestUtils.setField(
            sut, "cacheType", "caseId");

        when(dataStoreApi.getCaseDataV2("1234")).thenReturn(TestDataBuilder.buildCase());
        when(cacheManager.getCache(anyString())).thenReturn(caffeineCacheMock);
        when(caffeineCacheMock.getNativeCache()).thenReturn(cache);
        when(cache.estimatedSize()).thenReturn(anyLong());

        assertNotNull(sut.getCaseById("1234"));
        verify(cacheManager, times(1)).getCache(any());
        verify(caffeineCacheMock, times(1)).getNativeCache();
        verify(cache, times(1)).estimatedSize();
    }

    @Test
    void getCaseById_cacheNone() {

        org.springframework.test.util.ReflectionTestUtils.setField(
            sut, "cacheType", "none");

        when(dataStoreApi.getCaseDataV2("1234")).thenReturn(TestDataBuilder.buildCase());

        assertNotNull(sut.getCaseById("1234"));
        verify(cacheManager, times(0)).getCache(any());
        verify(caffeineCacheMock, times(0)).getNativeCache();
        verify(cache, times(0)).estimatedSize();

    }

    @Test
    void getCaseById_cacheNull() {

        org.springframework.test.util.ReflectionTestUtils.setField(
            sut, "cacheType", null);

        when(dataStoreApi.getCaseDataV2("1234")).thenReturn(TestDataBuilder.buildCase());

        assertNotNull(sut.getCaseById("1234"));
        verify(cacheManager, times(0)).getCache(any());
        verify(caffeineCacheMock, times(0)).getNativeCache();
        verify(cache, times(0)).estimatedSize();

    }

    @Test
    void getCaseById_withValidCache() {
        ReflectionTestUtils.setField(sut, "cacheType", "caseId");
        doReturn(caffeineCache).when(cacheManager).getCache(anyString());
        when(caffeineCache.getNativeCache()).thenReturn(nativeCache);
        when(dataStoreApi.getCaseDataV2("1234")).thenReturn(TestDataBuilder.buildCase());
        assertNotNull(sut.getCaseById("1234"));
    }

    @Test
    void getCaseById_withInValidCache() {
        ReflectionTestUtils.setField(sut, "cacheType", "none");
        when(dataStoreApi.getCaseDataV2("1234")).thenReturn(TestDataBuilder.buildCase());
        assertNotNull(sut.getCaseById("1234"));
    }
}
