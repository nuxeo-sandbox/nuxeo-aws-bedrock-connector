package org.nuxeo.labs.aws.bedrock;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.labs.aws.bedrock.automation.AWSBedrockInvokeOp;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class})
@Deploy("nuxeo-aws-bedrock-connector-core")
public class TestAWSBedrockInvokeOp {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void testSuccess() throws OperationException, IOException {
        Assume.assumeTrue(AwsCredentialChecker.isSet());
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("modelName","amazon.titan-embed-text-v2:0");
        params.put("jsonPayload","{\"inputText\":\"Hello\"}");
        Blob json = (Blob) automationService.run(ctx, AWSBedrockInvokeOp.ID, params);
        Assert.assertNotNull(json);
        JSONObject responseBody = new JSONObject(json.getString());
        double[] embeddings = responseBody.getJSONArray("embedding")
                .toList().stream().mapToDouble(v -> ((BigDecimal) v).doubleValue()).toArray();
        Assert.assertNotNull(embeddings);
        Assert.assertEquals(embeddings.length, 1024);
    }
}
