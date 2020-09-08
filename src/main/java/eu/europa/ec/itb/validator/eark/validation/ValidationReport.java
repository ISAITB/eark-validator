package eu.europa.ec.itb.validator.eark.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO to hold results from the second validate call.
 */
public class ValidationReport {

    @JsonProperty("metadata_valid")
    private Boolean metadataValid;
    @JsonProperty("profile_warnings")
    private Item[] profileWarnings;
    @JsonProperty("profile_errors")
    private Item[] profileErrors;
    @JsonProperty("schema_valid")
    private Boolean schemaValid;
    @JsonProperty("schema_errors")
    private String[] schemaErrors;

    public Boolean getMetadataValid() {
        return metadataValid;
    }

    public void setMetadataValid(Boolean metadataValid) {
        this.metadataValid = metadataValid;
    }

    public Item[] getProfileWarnings() {
        return profileWarnings;
    }

    public void setProfileWarnings(Item[] profileWarnings) {
        this.profileWarnings = profileWarnings;
    }

    public Item[] getProfileErrors() {
        return profileErrors;
    }

    public void setProfileErrors(Item[] profileErrors) {
        this.profileErrors = profileErrors;
    }

    public Boolean getSchemaValid() {
        return schemaValid;
    }

    public void setSchemaValid(Boolean schemaValid) {
        this.schemaValid = schemaValid;
    }

    public String[] getSchemaErrors() {
        return schemaErrors;
    }

    public void setSchemaErrors(String[] schemaErrors) {
        this.schemaErrors = schemaErrors;
    }

    public static class Item {

        private String location;
        private String message;
        @JsonProperty("rule_id")
        private String ruleId;
        private String severity;
        private String test;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRuleId() {
            return ruleId;
        }

        public void setRuleId(String ruleId) {
            this.ruleId = ruleId;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getTest() {
            return test;
        }

        public void setTest(String test) {
            this.test = test;
        }
    }

}
