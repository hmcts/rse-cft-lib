
package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.domain.model.Case;
import uk.gov.hmcts.reform.roleassignment.feignclients.DataStoreApi;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

@Service
@Slf4j
public class RetrieveDataService {
    //1. getting required case details(ccd data store)
    //2. getting required role details(some static reference data??)
    //3. getting required ticket details(Authorization table in JRD)
    //4. getting some location reference data

    private final DataStoreApi dataStoreApi;
    private CacheManager cacheManager;
    @Value("${spring.cache.type}")
    protected String cacheType;

    public RetrieveDataService(DataStoreApi dataStoreApi, CacheManager cacheManager) {
        this.dataStoreApi = dataStoreApi;
        this.cacheManager = cacheManager;
    }

    @Cacheable(value = "caseId")
    @Retryable(backoff = @Backoff(delay = 500, multiplier = 3))
    public Case getCaseById(String caseId) {
        if (Objects.nonNull(cacheType) && !cacheType.equals("none")) {
            var caffeineCache = (CaffeineCache) cacheManager.getCache("caseId");
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = requireNonNull(caffeineCache)
                .getNativeCache();
            log.info("Retrieving case details, current size of cache: {}", nativeCache.estimatedSize());
        }
        return dataStoreApi.getCaseDataV2(caseId);
    }
}
