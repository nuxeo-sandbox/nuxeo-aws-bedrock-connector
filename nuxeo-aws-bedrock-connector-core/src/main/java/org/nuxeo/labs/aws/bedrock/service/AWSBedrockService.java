package org.nuxeo.labs.aws.bedrock.service;

import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public interface AWSBedrockService {

    String invoke(String modelName, String jsonPayload);

    String invoke(String modelName, String jsonPayload, boolean useCache);

}
