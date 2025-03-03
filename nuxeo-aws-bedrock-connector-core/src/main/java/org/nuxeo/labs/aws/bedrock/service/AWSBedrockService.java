package org.nuxeo.labs.aws.bedrock.service;

public interface AWSBedrockService {

    String invoke(String modelName, String jsonPayload);

    String invoke(String modelName, String jsonPayload, boolean useCache);

}
