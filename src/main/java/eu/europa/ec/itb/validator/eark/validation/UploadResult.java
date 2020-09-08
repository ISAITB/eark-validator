package eu.europa.ec.itb.validator.eark.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO to hold results from the first upload call.
 */
public class UploadResult {

    private String message;
    @JsonProperty("sha1")
    private String digest;
    @JsonProperty("validation_url")
    private String validationUrl;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public String getValidationUrl() {
        return validationUrl;
    }

    public void setValidationUrl(String validationUrl) {
        this.validationUrl = validationUrl;
    }
}
