# Description
A plugin that provides a simple and easy to use integration pattern
between [AWS Bedrock](https://aws.amazon.com/bedrock/) and the Nuxeo Platform.

# How to build
```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-aws-bedrock-connector
cd nuxeo-aws-bedrock-connector
mvn clean install -DskipTests
```

# Features
## Automation API
The integration between the Nuxeo Platform and Bedrock is meant to be as versatile as possible and leverages Nuxeo's automation framework.

### Run Execution
The operation `Bedrock.Invoke` invokes the Bedrock API 

Parameters:

| Name        | Description                                 | Type            | Required | Default value |
|:------------|:--------------------------------------------|:----------------|:---------|:--------------|
| modelName   | The model technical name                    | string          | true     |               |
| jsonPayload | The json payload corresponding to the model | string          | true     |               |

Output: A string Blob containing the Bedrock REST API JSON response

Example on localhost:

```bash
curl --location 'http://localhost:8080/nuxeo/api/v1/automation/Bedrock.Invoke' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic ...' \
--data '{
    "params": {
        "modelName":"amazon.titan-embed-text-v2:0",
        "jsonPayload": "{\"inputText\":\"Hi Bedrock. Please give me the embedding for this text\"}"
    }
}'
```

# How to run
## Configuration
The following nuxeo.conf properties are available to configure the plugin

| Property name              | description                  |
|----------------------------|------------------------------|
| nuxeo.aws.bedrock.region   | The region to use (OPTIONAL) |

# Support
**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning
resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be
useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

# Nuxeo Marketplace
TODO

# License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo
Nuxeo Platform is an open source Content Services platform, written in Java. Data can be stored in both SQL & NoSQL
databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions
for [document management](https://www.nuxeo.com/solutions/document-management/), [case management](https://www.nuxeo.com/solutions/case-management/),
and [digital asset management](https://www.nuxeo.com/solutions/dam-digital-asset-management/), use cases. It uses
schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.nuxeo.com).