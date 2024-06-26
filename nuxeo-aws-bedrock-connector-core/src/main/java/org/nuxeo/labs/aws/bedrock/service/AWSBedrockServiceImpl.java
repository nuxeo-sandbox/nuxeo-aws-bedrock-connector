package org.nuxeo.labs.aws.bedrock.service;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.DefaultComponent;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;


public class AWSBedrockServiceImpl extends DefaultComponent implements AWSBedrockService {

    public InvokeModelResponse invoke(String modelName, String jsonPayload) {

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

            return client.invokeModel(request);
        }
    }
}
