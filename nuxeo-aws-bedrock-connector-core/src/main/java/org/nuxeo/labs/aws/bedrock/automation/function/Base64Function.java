package org.nuxeo.labs.aws.bedrock.automation.function;

import org.apache.commons.io.IOUtils;
import org.nuxeo.ecm.automation.context.ContextHelper;
import org.nuxeo.ecm.core.api.Blob;

import java.io.IOException;
import java.util.Base64;

public class Base64Function implements ContextHelper {

    public Base64Function() {}

    public String blob2Base64(Blob blob) throws IOException {
        byte[] fileContent = IOUtils.toByteArray(blob.getStream());
        return Base64.getEncoder().encodeToString(fileContent);
    }

    public String string2Base64(String text) throws IOException {
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

}

