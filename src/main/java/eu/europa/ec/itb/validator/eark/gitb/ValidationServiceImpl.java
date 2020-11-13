package eu.europa.ec.itb.validator.eark.gitb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitb.core.*;
import com.gitb.tr.*;
import com.gitb.tr.ObjectFactory;
import com.gitb.vs.Void;
import com.gitb.vs.*;
import eu.europa.ec.itb.validator.eark.validation.ValidationReport;
import eu.europa.ec.itb.validator.eark.validation.ValidationResult;
import eu.europa.ec.itb.validator.eark.validation.Validator;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Spring component that realises the validation service.
 */
@Component
public class ValidationServiceImpl extends BaseServiceImpl implements ValidationService {

    /** Logger. **/
    private static final Logger LOG = LoggerFactory.getLogger(ValidationServiceImpl.class);
    /** The name of the input parameter for the operation to perform. */
    public static final String INPUT__OPERATION = "operation";
    /** The name of the input parameter for the archive to test. */
    public static final String INPUT__ARCHIVE = "archive";
    /** The name of the input parameter for the archive's hash. */
    public static final String INPUT__DIGEST = "digest";
    /** The name of the report URL session data item. */
    public static final String INPUT__REPORT_URL = "reportUrl";
    /** Operation instructing the validator to only upload the archive and do the SHA check. */
    public static final String OPERATION__UPLOAD = "upload";
    /** Operation instructing the validator to only get a validation report from a provided URL. */
    public static final String OPERATION__REPORT = "report";

    @Value("${service.id}")
    private String serviceId;

    @Value("${service.version}")
    private String serviceVersion;

    @Value("${validator.tmpFolder}")
    private String tmpFolder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    /**
     * The purpose of the getModuleDefinition call is to inform its caller on how the service is supposed to be called.
     *
     * @param parameters No parameters are expected.
     * @return The response.
     */
    @Override
    public GetModuleDefinitionResponse getModuleDefinition(Void parameters) {
        GetModuleDefinitionResponse response = new GetModuleDefinitionResponse();
        response.setModule(new ValidationModule());
        response.getModule().setId(serviceId);
        response.getModule().setOperation("V");
        response.getModule().setMetadata(new Metadata());
        response.getModule().getMetadata().setName(response.getModule().getId());
        response.getModule().getMetadata().setVersion(serviceVersion);
        response.getModule().setInputs(new TypedParameters());
        response.getModule().getInputs().getParam().add(createParameter(INPUT__OPERATION, "string", UsageEnumeration.R, ConfigurationType.SIMPLE, String.format("The operation to perform (can be '%s' or '%s').", OPERATION__UPLOAD, OPERATION__REPORT)));
        response.getModule().getInputs().getParam().add(createParameter(INPUT__ARCHIVE, "binary", UsageEnumeration.O, ConfigurationType.BINARY, String.format("The archive to validate (required when operation is '%s').", OPERATION__UPLOAD)));
        response.getModule().getInputs().getParam().add(createParameter(INPUT__DIGEST, "string", UsageEnumeration.O, ConfigurationType.SIMPLE, String.format("The digest of the archive to validate (required when operation is '%s').", OPERATION__UPLOAD)));
        response.getModule().getInputs().getParam().add(createParameter(INPUT__REPORT_URL, "string", UsageEnumeration.O, ConfigurationType.SIMPLE, String.format("The validation report URL (required when operation is '%s').", OPERATION__REPORT)));
        return response;
    }

    /**
     * The validate operation is called to validate the input and produce a validation report.
     *
     * The expected input is described for the service's client through the getModuleDefinition call.
     *
     * @param parameters The input parameters and configuration for the validation.
     * @return The response containing the validation report.
     */
    @Override
    public ValidationResponse validate(ValidateRequest parameters) {
        ValidationResponse result = new ValidationResponse();
        // Extract and check the operation to perform.
        String operation = getRequiredInput(parameters.getInput(), INPUT__OPERATION);
        if (OPERATION__UPLOAD.equals(operation)) {
            // Extract inputs (archive and digest).
            String providedDigest = getRequiredInput(parameters.getInput(), INPUT__DIGEST);
            File inputArchive = new File(new File(tmpFolder), UUID.randomUUID().toString()+".zip");
            try {
                try {
                    inputArchive.getParentFile().mkdirs();
                    FileUtils.writeByteArrayToFile(inputArchive, Base64.getDecoder().decode(getRequiredInput(parameters.getInput(), INPUT__ARCHIVE)));

                } catch (IOException e) {
                    throw new IllegalStateException("Unable to write archive to file system", e);
                }
                result.setReport(toTAR(validator.upload(inputArchive, providedDigest), inputArchive, providedDigest, null));
            } finally {
                FileUtils.deleteQuietly(inputArchive);
            }
        } else if (OPERATION__REPORT.equals(operation)) {
            // Extract input (validation report URL).
            String reportUrl = getRequiredInput(parameters.getInput(), INPUT__REPORT_URL);
            result.setReport(toTAR(validator.validate(reportUrl), null, null, reportUrl));
        } else {
            throw new IllegalArgumentException(String.format("Unexpected value provided for input '%s'", INPUT__OPERATION));
        }
        return result;
    }

    /**
     * Convert validation result to a TAR (GITB validation report).
     *
     * @param result The result.
     * @param digestInput The received digest.
     * @param reportUrlInput The received report URL
     * @return The TAR instance.
     */
    private TAR toTAR(ValidationResult result, File archiveInput, String digestInput, String reportUrlInput) {
        TAR report = createEmptyReport();
        addInputs(report, digestInput, archiveInput, reportUrlInput);
        addOutputs(report, result);
        // Populate report.
        int errorCount = 0, warningCount = 0, infoCount = 0;
        if (result.getValidationReport() != null) {
            if (result.getValidationReport().getSchemaErrors() != null) {
                for (String error: result.getValidationReport().getSchemaErrors()) {
                    addReportItemError("[Schema] "+error, report.getReports().getInfoOrWarningOrError());
                }
                errorCount += result.getValidationReport().getSchemaErrors().length;
            }
            if (result.getValidationReport().getProfileErrors() != null) {
                for (ValidationReport.Item item: result.getValidationReport().getProfileErrors()) {
                    processValidationReportItem("Profile", item, report.getReports().getInfoOrWarningOrError());
                }
                errorCount += result.getValidationReport().getProfileErrors().length;
            }
            if (result.getValidationReport().getProfileWarnings() != null) {
                for (ValidationReport.Item item: result.getValidationReport().getProfileWarnings()) {
                    processValidationReportItem("Profile", item, report.getReports().getInfoOrWarningOrError());
                }
                warningCount += result.getValidationReport().getProfileWarnings().length;
            }
        }
        if (result.getUploadResult() != null) {
            if (result.getUploadResult().getMessage() != null) {
                errorCount += 1;
                addReportItemError(result.getUploadResult().getMessage(), report.getReports().getInfoOrWarningOrError());
            }
        }
        // Set counters.
        report.getCounters().setNrOfErrors(BigInteger.valueOf(errorCount));
        report.getCounters().setNrOfWarnings(BigInteger.valueOf(warningCount));
        report.getCounters().setNrOfAssertions(BigInteger.valueOf(infoCount));
        // Set overall result.
        if (errorCount > 0) {
            report.setResult(TestResultType.FAILURE);
        } else if (warningCount > 0) {
            report.setResult(TestResultType.WARNING);
        } else {
            report.setResult(TestResultType.SUCCESS);
        }
        return report;
    }

    /**
     * Add input values to the report's context.
     *
     * @param report The report.
     * @param digestInput The provided digest.
     * @param archiveInput The provided archive.
     * @param reportUrlInput The provided report URL.
     */
    private void addInputs(TAR report, String digestInput, File archiveInput, String reportUrlInput) {
        AnyContent inputMap = new AnyContent();
        inputMap.setName("input");
        if (archiveInput != null) {
            try {
                inputMap.getItem().add(createAnyContent("archive", Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(archiveInput)), "binary", ValueEmbeddingEnumeration.BASE_64));
            } catch (IOException e) {
                LOG.warn("Error while producing Base64 representation of input archive", e);
            }
        }
        if (digestInput != null) {
            inputMap.getItem().add(createAnyContent("digest", digestInput, "string", ValueEmbeddingEnumeration.STRING));
        }
        if (reportUrlInput != null) {
            inputMap.getItem().add(createAnyContent("reportUrl", reportUrlInput, "string", ValueEmbeddingEnumeration.STRING));
        }
        report.getContext().getItem().add(inputMap);
    }

    /**
     * Add output values to the report's context.
     *
     * @param report The report.
     * @param result The result to use.
     */
    private void addOutputs(TAR report, ValidationResult result) {
        AnyContent outputMap = new AnyContent();
        outputMap.setName("output");
        if (result.getUploadResult() != null) {
            try {
                outputMap.getItem().add(createAnyContent("upload", replaceBadCharacters(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.getUploadResult())), "string", ValueEmbeddingEnumeration.STRING));
                if (result.getUploadResult().getValidationUrl() != null) {
                    outputMap.getItem().add(createAnyContent("reportUrl", result.getUploadResult().getValidationUrl(), "string", ValueEmbeddingEnumeration.STRING));
                }
            } catch (JsonProcessingException e) {
                LOG.warn("Unable to serialise upload result", e);
            }
        }
        if (result.getValidationReport() != null) {
            try {
                outputMap.getItem().add(createAnyContent("validation", replaceBadCharacters(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.getValidationReport())), "string", ValueEmbeddingEnumeration.STRING));
            } catch (JsonProcessingException e) {
                LOG.warn("Unable to serialise validation result", e);
            }
        }
        report.getContext().getItem().add(outputMap);
    }

    /**
     * Replace non-ascii characters that may cause issues generating reports.
     *
     * @param input The input to process.
     * @return The output.
     */
    private String replaceBadCharacters(String input) {
        String output = null;
        if (input != null) {
            output = input
                    .replace('\u201c', '"')
                    .replace('\u201d', '"');
        }
        return output;
    }

    /**
     * Map an item from the received validation report to a TAR report item.
     *
     * @param prefix The prefix to add for the report item's message.
     * @param item The item to process.
     * @param reportItems The TAR items to add to.
     */
    private void processValidationReportItem(String prefix, ValidationReport.Item item, List<JAXBElement<TestAssertionReportType>> reportItems) {
        String messageToSet = "["+prefix+"]";
        if (item.getRuleId() != null) {
            messageToSet += "["+item.getRuleId()+"]";
        }
        if (item.getMessage() != null) {
            // Remove non-ASCII characters.
            messageToSet += " " + replaceBadCharacters(item.getMessage());
        }
        BAR itemContent = createReportItemContent(messageToSet, item.getTest(), item.getRuleId(), item.getLocation());
        if ("Warn".equals(item.getSeverity())) {
            addReportItemWarning(itemContent, reportItems);
        } else {
            addReportItemError(itemContent, reportItems);
        }
    }

}
