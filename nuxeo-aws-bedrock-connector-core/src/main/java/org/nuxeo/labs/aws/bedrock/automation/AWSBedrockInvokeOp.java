package org.nuxeo.labs.aws.bedrock.automation;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.labs.aws.bedrock.service.AWSBedrockService;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Operation(id = AWSBedrockInvokeOp.ID, category = "AWS", label = "Invoke Bedrock and return the JSON response as a blob",
        description = "Invoke the AWS Bedrock API")
public class AWSBedrockInvokeOp {

    public static final String ID = "Bedrock.GetEmbedding";

    @Param(name = "modelName", required = true)
    protected String modelName;

    @Param(name = "jsonPayload", required = true)
    protected String jsonPayload;

    @Context
    AWSBedrockService awsBedrockService;

    @OperationMethod
    public Blob run() {
        InvokeModelResponse response = awsBedrockService.invoke(modelName, jsonPayload);
        return new StringBlob(response.body().asUtf8String(), "application/json");
    }

}
