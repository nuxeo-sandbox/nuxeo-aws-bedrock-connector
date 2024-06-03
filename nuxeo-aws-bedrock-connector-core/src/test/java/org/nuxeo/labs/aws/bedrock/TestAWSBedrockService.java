package org.nuxeo.labs.aws.bedrock;

import static org.junit.Assert.assertNotNull;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.labs.aws.bedrock.service.AWSBedrockService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

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
        String titanModelId = "amazon.titan-embed-text-v2:0";
        String payload = """
                {
                    "inputText":"This some sample text"
                }"
                """;
        InvokeModelResponse response = awsbedrockservice.invoke(titanModelId, payload);
        JSONObject responseBody = new JSONObject(response.body().asUtf8String());
        double[] embeddings = responseBody.getJSONArray("embedding")
                .toList().stream().mapToDouble(v -> ((BigDecimal) v).doubleValue()).toArray();
        Assert.assertNotNull(embeddings);
        Assert.assertEquals(embeddings.length, 1024);
    }

    @Test
    public void testGetImageEmbeddings() throws IOException {
        byte[] fileContent = FileUtils.readFileToByteArray(new File(getClass().getResource("/files/musubimaru.png").getPath()));
        String encodedString = Base64.getEncoder().encodeToString(fileContent);

        String titanModelId = "amazon.titan-embed-image-v1";
        String payload = String.format("""
                {
                    "inputText" : "An image that shows the mascot of sendai city in japan eating a rice ball",
                    "inputImage": "%s"
                }"
                """, encodedString);
        InvokeModelResponse response = awsbedrockservice.invoke(titanModelId, payload);
    }

}
