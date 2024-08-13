package org.nuxeo.labs.aws.bedrock.service;

import org.nuxeo.ecm.core.cache.Cache;
import org.nuxeo.ecm.core.cache.CacheService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;


public class AWSBedrockServiceImpl extends DefaultComponent implements AWSBedrockService {

    public static final String BEDROCK_CACHE = "bedrock_cache";

    public String invoke(String modelName, String jsonPayload) {
        return invoke(modelName,jsonPayload,false);
    }

    public String invoke(String modelName, String jsonPayload, boolean useCache) {

        if (useCache) {
            CacheService cacheService = Framework.getService(CacheService.class);
            Cache cache = cacheService.getCache(BEDROCK_CACHE);
            String cacheKey = getCacheKey(modelName,jsonPayload);
            if (cache.hasEntry(cacheKey)) {
                return (String) cache.get(cacheKey);
            }
        }

        String region = Framework.getProperty("nuxeo.aws.bedrock.region");

        try (BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .region(region != null ? Region.of(region) : null)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .body(SdkBytes.fromUtf8String(jsonPayload))
                    .modelId(modelName)
                    .contentType("application/json")
                    .accept("application/json")
                    .build();

            String response = client.invokeModel(request).body().asUtf8String();

            if (useCache) {
                CacheService cacheService = Framework.getService(CacheService.class);
                Cache cache = cacheService.getCache(BEDROCK_CACHE);
                cache.put(getCacheKey(modelName,jsonPayload), response);
            }

            return response;
        }
    }

    public static String getCacheKey(String modelName, String jsonPayload) {
        return modelName+jsonPayload;
    }

}
