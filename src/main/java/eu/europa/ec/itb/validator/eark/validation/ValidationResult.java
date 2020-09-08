package eu.europa.ec.itb.validator.eark.validation;

/**
 * A wrapper class for the results of both backend service calls.
 */
public class ValidationResult {

    private UploadResult uploadResult;
    private ValidationReport validationReport;

    public ValidationResult(UploadResult uploadResult, ValidationReport validationReport) {
        this.uploadResult = uploadResult;
        this.validationReport = validationReport;
    }

    public UploadResult getUploadResult() {
        return uploadResult;
    }

    public ValidationReport getValidationReport() {
        return validationReport;
    }
}
