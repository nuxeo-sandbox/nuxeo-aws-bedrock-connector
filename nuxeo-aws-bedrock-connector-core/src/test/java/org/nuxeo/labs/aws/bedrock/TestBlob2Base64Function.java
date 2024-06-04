package org.nuxeo.labs.aws.bedrock;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.labs.aws.bedrock.automation.function.Blob2Base64Function;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import java.io.File;
import java.io.IOException;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class})
@Deploy("nuxeo-aws-bedrock-connector-core")
public class TestBlob2Base64Function {

    @Test
    public void testBase64Conversion() throws IOException {
        Blob blob = new FileBlob(new File(getClass().getResource("/files/musubimaru.png").getPath()));
        Blob2Base64Function fn = new Blob2Base64Function();
        String base64str =  fn.toBase64(blob);
        Assert.assertTrue(StringUtils.isNotBlank(base64str));
    }
}
