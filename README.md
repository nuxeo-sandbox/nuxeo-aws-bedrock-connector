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

#### • `Bedrock.Invoke` Operation
The operation `Bedrock.Invoke` invokes the Bedrock API.

Parameters:

| Name        | Description                                 | Type    | Required | Default value |
|:------------|:--------------------------------------------|:--------|:---------|:--------------|
| modelName   | The model technical name                    | string  | true     |               |
| jsonPayload | The json payload corresponding to the model | string  | true     |               |
| useCache    | Use cached response                         | boolean | false    | false         |

Output: A string Blob containing the Bedrock REST API JSON response. Use its `getString()` method to get the JSON String (see Automation Scripting example below)

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

#### • `Base64Helper` Automation Helper

The plugin also provides the `Base64Helper` Automation Helper, that allows for creating the Base64 representation of a blob or a String:

* `Base64Helper.blob2Base64(aBlob)`
* `Base64Helper.string2Base64(aString)`

(See Automation Script example below)

### Automation Script Example: Describe an Image

```js
function run(input, params) {
  // Get a rendition (don't send a 300MB Photoshop))
  var blob = Picture.GetView(input, {'viewName': 'FullHD'});
  // Encode with the helper
  var base64 = Base64Helper.blob2Base64(blob);
  // Prepare the call to Bedrock
  var payload = {
    "messages": [
      {
        "role": "user",
        "content": [
          {
            "type": "image",
            "source": {
              "type": "base64",
              "media_type": "image/jpeg",
              "data": base64
            }
          },
          {
            "type": "text",
            "text": "Describe the content of the image"
          }
        ]
      }
    ],
    "max_tokens": 512,
    "anthropic_version": "bedrock-2023-05-31"
  };
  // Call the operation
  var responseBlob = Bedrock.Invoke(null, {
    'jsonPayload': JSON.stringify(payload),
    'modelName': "anthropic.claude-3-sonnet-20240229-v1:0"
  });
  // Get the result
  var response = JSON.parse(responseBlob.getString());
  input["dc:description"] = response.content[0].text;
  
  input = Document.Save(input, {});

  return input;
}
```

The prompt could be parametrized. "Describe shortly the content of the image". And/or in another language "Décris de façon succinte le contenu de l'image", etc. The model used (here, `anthropic.claude-3-sonnet-20240229-v1:0`, also could be parametrized in the chain.)


## Vector Search
Vector search enables use cases such as semantic search and RAG.
A [sample configuration template](./nuxeo-aws-bedrock-connector-package/src/main/resources/install/templates/embedding-sample) is provided in this plugin 

### Configuration
This feature is implemented only for OpenSearch 1.3.x. In order to use the feature, knn must be enabled at the index level. This can only be done with a package configuration template.
A sample index configuration is available [here](./nuxeo-aws-bedrock-connector-package/src/main/resources/install/templates/opensearch-knn/nxserver/config/elasticsearch-doc-settings.json.nxftl)

Vector fields must be explicitly declared in the index mapping.

> [!IMPORTANT]
> The `dimension` property must correspond to the embbedings size (see below, "Embedding generation")

```json
{
  "embedding:text": {
    "type": "knn_vector",
    "dimension": 1024
  },
  "embedding:image": {
    "type": "knn_vector",
    "dimension": 1024,
    "method": {
      "name": "hnsw",
      "space_type": "l2",
      "engine": "nmslib",
      "parameters": {
        "ef_construction": 128,
        "m": 24
      }
    }
  }
}
```
This can be done by overriding the whole mapping configuration in a package configuration template or by using Nuxeo Studio.

### Embedding generation

Embbedings can be generated using event handlers and automation scripts. Below is an example of generating embeddings for images using AWS Titan multimodal model.

```js
function run(input, params) {

  var blob = Picture.GetView(input, {
    'viewName': 'FullHD'
  });

  var base64 = Base64Helper.blob2Base64(blob);

  // Notice the outputEmbeddingLength of 1024, matching the "dimension" property of the index
  var payload = {
    "inputImage": base64,
    "embeddingConfig": {
     "outputEmbeddingLength": 1024
     }
  };

  var responseBlob = Bedrock.Invoke(null, {
    'jsonPayload': JSON.stringify(payload),
    'modelName': 'amazon.titan-embed-image-v1',
    'useCache': true
  });

  var response = JSON.parse(responseBlob.getString());

  input['embedding:image'] = response.embedding;

  input = Document.Save(input, {});

  return input;
}
```

### Vector Search
This plugin includes [an implementation of the pageprovider](./nuxeo-aws-bedrock-connector-core/src/main/java/org/nuxeo/labs/aws/bedrock/search/pp/VectorSearchPageProvider.java) interface that bring vector search capabilities to the Nuxeo search API. 
The pageprovider exposes several named parameters:

| Named Parameter                | Description                                                                      | Type    | Required | Default value |
|:-------------------------------|:---------------------------------------------------------------------------------|:--------|:---------|:--------------|
| vector_index                   | The vector field name to use for search                                          | string  | true     |               |
| vector_value                   | The input vector                                                                 | string  | false    |               |
| input_text                     | A text string can be passed instead of a vector                                  | string  | false    |               |
| embedding_automation_processor | The automation chain/script to use to convert `input_text` to a vector embedding | boolean | false    |               |
| k                              | The k value for knn                                                              | integer | false    | 10            |
| min_score                      | The min_score for results the a hit must satisfied                               | float   | false    | 0.4           |

The search input is either `vector_value` or the combination `input_text` and `embedding_automation_processor`. 
For the latter, the model used to generate the embedding must be same as the model used to generate the embedding vectors for `vector_index`

Here's an example of call

```curl
curl 'http://localhost:8080/nuxeo/api/v1/search/pp/simple-vector-search/execute?input_text=japanese%20kei%20car&vector_index=embedding%3Aimage&embedding_automation_processor=javascript.text2embedding&k=10' \
  -H 'Content-Type: application/json' \
  -H 'accept: text/plain,application/json, application/json' \
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
