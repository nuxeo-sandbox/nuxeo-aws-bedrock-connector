package org.nuxeo.labs.aws.bedrock;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.labs.aws.bedrock.automation.function.Base64Function;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import java.io.File;
import java.io.IOException;

@RunWith(FeaturesRunner.class)
@Features({AutomationFeature.class})
@Deploy("nuxeo-aws-bedrock-connector-core")
public class TestBase64Function {

    @Test
    public void testBlob2Base64Conversion() throws IOException {
        Blob blob = new FileBlob(new File(getClass().getResource("/files/musubimaru.png").getPath()));
        Base64Function fn = new Base64Function();
        String base64str = fn.blob2Base64(blob);
        Assert.assertTrue(StringUtils.isNotBlank(base64str));
    }

    @Test
    public void testString2Base64Conversion() throws IOException {
        Base64Function fn = new Base64Function();
        String base64str = fn.string2Base64("This is a test");
        Assert.assertEquals("VGhpcyBpcyBhIHRlc3Q=", base64str);
    }
}
