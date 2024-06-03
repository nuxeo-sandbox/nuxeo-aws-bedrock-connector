package org.nuxeo.labs.aws.bedrock.service;

import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public interface AWSBedrockService {

    InvokeModelResponse invoke(String modelName, String jsonPayload);

}
