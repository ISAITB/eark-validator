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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

/**
 * Spring component that realises the validation service.
 */
@Component
public class ValidationServiceImpl implements ValidationService {

    /** Logger. **/
    private static final Logger LOG = LoggerFactory.getLogger(ValidationServiceImpl.class);

    /** The name of the input parameter for the archive to test. */
    public static final String INPUT__ARCHIVE = "archive";
    /** The name of the input parameter for the archive's hash. */
    public static final String INPUT__DIGEST = "digest";

    @Value("${service.id}")
    private String serviceId;

    @Value("${service.version}")
    private String serviceVersion;

    @Value("${validator.tmpFolder}")
    private String tmpFolder;

    @Autowired
    private ObjectFactory objectFactory;

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
        response.getModule().getInputs().getParam().add(createParameter(INPUT__ARCHIVE, "binary", UsageEnumeration.R, ConfigurationType.SIMPLE, "The archive to validate."));
        response.getModule().getInputs().getParam().add(createParameter(INPUT__DIGEST, "string", UsageEnumeration.R, ConfigurationType.SIMPLE, "The archive's SHA1 digest."));
        return response;
    }

    /**
     * Create a parameter definition.
     *
     * @param name The name of the parameter.
     * @param type The type of the parameter. This needs to match one of the GITB types.
     * @param use The use (required or optional).
     * @param kind The kind og parameter it is (whether it should be provided as the specific value, as BASE64 content or as a URL that needs to be looked up to obtain the value).
     * @param description The description of the parameter.
     * @return The created parameter.
     */
    private TypedParameter createParameter(String name, String type, UsageEnumeration use, ConfigurationType kind, String description) {
        TypedParameter parameter =  new TypedParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setUse(use);
        parameter.setKind(kind);
        parameter.setDesc(description);
        return parameter;
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
        // First extract the parameters and check to see if they are as expected.
        List<AnyContent> archiveInput = getInput(parameters, INPUT__ARCHIVE);
        if (archiveInput.size() != 1) {
            throw new IllegalArgumentException(String.format("This service expects one input to be provided named '%s'", INPUT__ARCHIVE));
        }
        List<AnyContent> hashInput = getInput(parameters, INPUT__DIGEST);
        if (hashInput.size() != 1) {
            throw new IllegalArgumentException(String.format("This service expects one input to be provided named '%s'", INPUT__DIGEST));
        }
        String providedDigest = hashInput.get(0).getValue();
        // Run the validation and extract the report.
        File inputArchive = new File(new File(tmpFolder), UUID.randomUUID().toString()+".zip");
        try {
            inputArchive.getParentFile().mkdirs();
            FileUtils.writeByteArrayToFile(inputArchive, Base64.getDecoder().decode(archiveInput.get(0).getValue()));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write archive to file system", e);
        }
        try {
            result.setReport(toTAR(validator.validate(inputArchive, providedDigest), inputArchive, providedDigest));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("An error occurred while validating the archive", e);
        } finally {
            FileUtils.deleteQuietly(inputArchive);
        }
    }

    /**
     * Add input values to the report's context.
     *
     * @param report The report.
     * @param digestInput The provided digest.
     * @param archiveInput The provided archive.
     */
    private void addInputs(TAR report, String digestInput, File archiveInput) {
        AnyContent inputMap = new AnyContent();
        inputMap.setName("input");
        try {
            inputMap.getItem().add(createAnyContent("archive", Base64.getEncoder().encodeToString(FileUtils.readFileToByteArray(archiveInput)), "binary", ValueEmbeddingEnumeration.BASE_64));
        } catch (IOException e) {
            LOG.warn("Error while producing Base64 representation of input archive", e);
        }
        inputMap.getItem().add(createAnyContent("digest", digestInput, "string", ValueEmbeddingEnumeration.STRING));
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

    /**
     * Convert validation result to a TAR (GITB validation report).
     *
     * @param result The result.
     * @param digestInput The recevied digest.
     * @return The TAR instance.
     */
    private TAR toTAR(ValidationResult result, File archiveInput, String digestInput) {
        TAR report = new TAR();
        report.setDate(getCurrentDate());
        // Add context items.
        report.setContext(new AnyContent());
        addInputs(report, digestInput, archiveInput);
        addOutputs(report, result);
        // Populate report.
        int errorCount = 0, warningCount = 0, infoCount = 0;
        report.setReports(new TestAssertionGroupReportsType());
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
        } else {
            errorCount += 1;
            if (result.getUploadResult() != null && result.getUploadResult().getMessage() != null) {
                addReportItemError(result.getUploadResult().getMessage(), report.getReports().getInfoOrWarningOrError());
            }
        }
        // Set counters.
        report.setCounters(new ValidationCounters());
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
     * Lookup a provided input from the received request parameters.
     *
     * @param parameters The request's parameters.
     * @param inputName The name of the input to lookup.
     * @return The inputs found to match the parameter name (not null).
     */
    private List<AnyContent> getInput(ValidateRequest parameters, String inputName) {
        List<AnyContent> inputs = new ArrayList<>();
        if (parameters != null) {
            if (parameters.getInput() != null) {
                for (AnyContent anInput: parameters.getInput()) {
                    if (inputName.equals(anInput.getName())) {
                        inputs.add(anInput);
                    }
                }
            }
        }
        return inputs;
    }

    /**
     * Add an error message to the report.
     *
     * @param message The message.
     * @param reportItems The report's items.
     */
    private void addReportItemError(String message, List<JAXBElement<TestAssertionReportType>> reportItems) {
        reportItems.add(objectFactory.createTestAssertionGroupReportsTypeError(createReportItemContent(message, null, null, null)));
    }

    /**
     * Add an error item to the report.
     *
     * @param item The item.
     * @param reportItems The report's items.
     */
    private void addReportItemError(BAR item, List<JAXBElement<TestAssertionReportType>> reportItems) {
        reportItems.add(objectFactory.createTestAssertionGroupReportsTypeError(item));
    }

    /**
     * Add a warning item to the report.
     *
     * @param item The item.
     * @param reportItems The report's items.
     */
    private void addReportItemWarning(BAR item, List<JAXBElement<TestAssertionReportType>> reportItems) {
        reportItems.add(objectFactory.createTestAssertionGroupReportsTypeWarning(item));
    }

    /**
     * Create the internal content of a report's item.
     *
     * @param message The message.
     * @return The content to wrap.
     */
    private BAR createReportItemContent(String message, String test, String ruleId, String location) {
        BAR itemContent = new BAR();
        itemContent.setDescription(message);
        itemContent.setTest(test);
        itemContent.setAssertionID(ruleId);
        itemContent.setLocation(location);
        return itemContent;
    }

    /**
     * Create a AnyContent object value based on the provided parameters.
     *
     * @param name The name of the value.
     * @param value The value itself.
     * @param type The data type of the content.
     * @param embeddingMethod The way in which this value is to be considered.
     * @return The value.
     */
    private AnyContent createAnyContent(String name, String value, String type, ValueEmbeddingEnumeration embeddingMethod) {
        AnyContent input = new AnyContent();
        input.setName(name);
        input.setValue(value);
        input.setType(type);
        input.setEmbeddingMethod(embeddingMethod);
        return input;
    }

    /**
     * Get the current date as a XMLGregorianCalendar.
     *
     * @return The calendar.
     */
    private XMLGregorianCalendar getCurrentDate() {
        GregorianCalendar calendar = new GregorianCalendar();
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("Unable to construct data type factory for date", e);
        }
    }

}
