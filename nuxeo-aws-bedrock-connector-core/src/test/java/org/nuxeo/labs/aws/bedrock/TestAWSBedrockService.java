package org.nuxeo.labs.aws.bedrock;

import static org.junit.Assert.assertNotNull;
import static org.nuxeo.labs.aws.bedrock.service.AWSBedrockServiceImpl.BEDROCK_CACHE;
import static org.nuxeo.labs.aws.bedrock.service.AWSBedrockServiceImpl.getCacheKey;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.cache.Cache;
import org.nuxeo.ecm.core.cache.CacheService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.aws.bedrock.service.AWSBedrockService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;

@RunWith(FeaturesRunner.class)
@Features({PlatformFeature.class})
@Deploy("nuxeo-aws-bedrock-connector-core")
public class TestAWSBedrockService {

    @Inject
    protected AWSBedrockService awsbedrockservice;

    @Test
    public void testService() {
        assertNotNull(awsbedrockservice);
    }

    @Test
    public void testGetTextEmbeddings() {
        Assume.assumeTrue(AwsCredentialChecker.isSet());
        String titanModelId = "amazon.titan-embed-text-v2:0";
        String payload = """
                {
                    "inputText":"This some sample text"
                }"
                """;
        String response = awsbedrockservice.invoke(titanModelId, payload);
        JSONObject responseBody = new JSONObject(response);
        double[] embeddings = responseBody.getJSONArray("embedding")
                .toList().stream().mapToDouble(v -> ((BigDecimal) v).doubleValue()).toArray();
        Assert.assertNotNull(embeddings);
        Assert.assertEquals(embeddings.length, 1024);
    }

    @Test
    public void testGetImageEmbeddings() throws IOException {
        Assume.assumeTrue(AwsCredentialChecker.isSet());
        byte[] fileContent = FileUtils.readFileToByteArray(new File(getClass().getResource("/files/musubimaru.png").getPath()));
        String encodedString = Base64.getEncoder().encodeToString(fileContent);

        String titanModelId = "amazon.titan-embed-image-v1";
        String payload = String.format("""
                {
                    "inputText" : "An image that shows the mascot of sendai city in japan eating a rice ball",
                    "inputImage": "%s"
                }
                """, encodedString);
        String response = awsbedrockservice.invoke(titanModelId, payload);
        JSONObject responseBody = new JSONObject(response);
        double[] embeddings = responseBody.getJSONArray("embedding")
                .toList().stream().mapToDouble(v -> ((BigDecimal) v).doubleValue()).toArray();
        Assert.assertNotNull(embeddings);
        Assert.assertEquals(embeddings.length, 1024);
    }

    @Test
    public void testResponseCaching() {
        Assume.assumeTrue(AwsCredentialChecker.isSet());
        String titanModelId = "amazon.titan-embed-text-v2:0";
        String payload = """
                {
                    "inputText":"This some sample text"
                }"
                """;
        String response = awsbedrockservice.invoke(titanModelId, payload, true);
        Assert.assertNotNull(response);

        CacheService cacheService = Framework.getService(CacheService.class);
        Cache cache = cacheService.getCache(BEDROCK_CACHE);
        Assert.assertTrue(cache.hasEntry(getCacheKey(titanModelId,payload)));
    }

    @Test
    public void testCacheHit() {
        Assume.assumeTrue(AwsCredentialChecker.isSet());
        String modelId = "the model that don't exist yet";
        String payload = """
                {
                    "inputText":"Let's see some magic"
                }"
                """;

        String cachedResponse = "123";

        CacheService cacheService = Framework.getService(CacheService.class);
        Cache cache = cacheService.getCache(BEDROCK_CACHE);
        cache.put(getCacheKey(modelId,payload),cachedResponse);

        String response = awsbedrockservice.invoke(modelId, payload, true);
        Assert.assertEquals(cachedResponse, response);
    }

}
