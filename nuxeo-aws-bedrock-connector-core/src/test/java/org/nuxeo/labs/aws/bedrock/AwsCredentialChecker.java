package org.nuxeo.labs.aws.bedrock;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

public class AwsCredentialChecker {

    static boolean isSet() {
        try (DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create()) {
            AwsCredentials credentials = credentialsProvider.resolveCredentials();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
