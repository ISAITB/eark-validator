package eu.europa.ec.itb.validator.eark.gitb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitb.core.*;
import com.gitb.ps.Void;
import com.gitb.ps.*;
import com.gitb.tr.ObjectFactory;
import com.gitb.tr.*;
import eu.europa.ec.itb.validator.eark.validation.ValidationReport;
import eu.europa.ec.itb.validator.eark.validation.ValidationResult;
import eu.europa.ec.itb.validator.eark.validation.Validator;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBElement;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessingServiceImpl extends BaseServiceImpl implements ProcessingService {

    /** Logger. **/
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingServiceImpl.class);

    /** The name of the input parameter for the archive to test. */
    public static final String INPUT__ARCHIVE = "archive";
    /** The name of the input parameter for the archive's hash. */
    public static final String INPUT__DIGEST = "digest";
    /** The name of the report URL session data item. */
    public static final String INPUT__REPORT_URL = "reportUrl";
    /** The name of the initialise operation. */
    public static final String OPERATION__INITIALISE = "initialise";
    /** The name of the output parameter for the validator's session. */
    public static final String OUTPUT__SESSION = "session";

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

    // Map used to record the ongoing validation sessions.
    private Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();

    /**
     * The purpose of the getModuleDefinition call is to inform its caller on how the service is supposed to be called.
     *
     * @param aVoid No parameters are expected.
     * @return The response.
     */
    @Override
    public GetModuleDefinitionResponse getModuleDefinition(Void aVoid) {
        GetModuleDefinitionResponse response = new GetModuleDefinitionResponse();
        response.setModule(new ProcessingModule());
        response.getModule().setId(serviceId);
        response.getModule().setMetadata(new Metadata());
        response.getModule().getMetadata().setVersion(serviceVersion);
        response.getModule().setConfigs(new ConfigurationParameters());
        TypedParameter inputArchive = createParameter(INPUT__ARCHIVE, "binary", UsageEnumeration.R, ConfigurationType.BINARY, "The archive to validate.");
        TypedParameter inputDigest = createParameter(INPUT__DIGEST, "string", UsageEnumeration.R, ConfigurationType.SIMPLE, "The archive's SHA1 digest.");
        TypedParameter outputSession = createParameter(OUTPUT__SESSION, "string", UsageEnumeration.R, ConfigurationType.SIMPLE, "The session ID to use for subsequent calls to the validator.");
        response.getModule().getOperation().add(createProcessingOperation(OPERATION__INITIALISE, List.of(inputArchive, inputDigest), List.of(outputSession)));
        return response;
    }

    /**
     * Create the service's processing operation documentation.
     *
     * @param name The name of the operation.
     * @param input The operation inputs.
     * @param output The operation outputs.
     * @return The operation definition.
     */
    private ProcessingOperation createProcessingOperation(String name, List<TypedParameter> input, List<TypedParameter> output) {
        ProcessingOperation operation = new ProcessingOperation();
        operation.setName(name);
        operation.setInputs(new TypedParameters());
        operation.getInputs().getParam().addAll(input);
        operation.setOutputs(new TypedParameters());
        operation.getOutputs().getParam().addAll(output);
        return operation;
    }

    /**
     * Used to initialise the validator through the 'initialise' operation.
     *
     * In this operation the archive and digest are provided that will be subsequently used for the different validation
     * calls.
     *
     * @param processRequest The service's input.
     * @return The service's output.
     */
    @Override
    public ProcessResponse process(ProcessRequest processRequest) {
        ProcessResponse response = new ProcessResponse();
        if (processRequest.getSessionId() == null) {
            throw new IllegalArgumentException("No session ID was provided");
        }
        if (!sessions.containsKey(processRequest.getSessionId())) {
            throw new IllegalArgumentException(String.format("Session with ID '%s' was not found", processRequest.getSessionId()));
        }
        // Extract inputs (archive and digest).
        String providedDigest = getRequiredInput(processRequest.getInput(), INPUT__DIGEST);
        File inputArchive = new File(new File(tmpFolder), UUID.randomUUID().toString()+".zip");
        try {
            inputArchive.getParentFile().mkdirs();
            FileUtils.writeByteArrayToFile(inputArchive, Base64.getDecoder().decode(getRequiredInput(processRequest.getInput(), INPUT__ARCHIVE)));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write archive to file system", e);
        }
        // Store values for later use by validator calls.
        if (processRequest.getSessionId() != null) {
            Map<String, Object> sessionData = sessions.get(processRequest.getSessionId());
            sessionData.put(INPUT__DIGEST, providedDigest);
            sessionData.put(INPUT__ARCHIVE, inputArchive.getAbsolutePath());
        }
        // We need to return the session ID to ensure validator calls can refer to the provided input.
        response.getOutput().add(createAnyContent(OUTPUT__SESSION, processRequest.getSessionId(), "string", ValueEmbeddingEnumeration.STRING));
        response.setReport(createEmptyReport());
        return response;
    }

    /**
     * Validation data (the archive and digest) need to be recorded across service calls. For this reason
     * we support transactions and record the provided data in a simple session structure.
     *
     * The main purpose of this operation is to create a session and return its identifier to the test bed.
     *
     * @param beginTransactionRequest The begin transaction signal.
     * @return The response.
     */
    @Override
    public BeginTransactionResponse beginTransaction(BeginTransactionRequest beginTransactionRequest) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new HashMap<>());
        BeginTransactionResponse response = new BeginTransactionResponse();
        response.setSessionId(sessionId);
        return response;
    }

    /**
     * Called once a test session or processing transaction ends.
     *
     * The purpose of this operation is to lookup the existing session and remove its data.
     *
     * @param basicRequest The end transaction signal.
     * @return The response.
     */
    @Override
    public Void endTransaction(BasicRequest basicRequest) {
        if (basicRequest.getSessionId() != null) {
            // Clear session data.
            Map<String, Object> sessionData = sessions.get(basicRequest.getSessionId());
            if (sessionData != null && sessionData.containsKey(INPUT__ARCHIVE)) {
                File inputArchive = Path.of((String)sessionData.get(INPUT__ARCHIVE)).toFile();
                FileUtils.deleteQuietly(inputArchive);
            }
            sessions.remove(basicRequest.getSessionId());
        }
        return new Void();
    }

    /**
     * This operation is called through a validation step to make the upload call on the backend validator.
     *
     * This is implemented here because we need to leverage the service's state (set up through the initialise operation).
     * Upon successful upload call, this operation also records the returned validation report URL.
     *
     * @param sessionId The session ID to refer to.
     * @return The validation report.
     */
    public TAR callUploadForSession(String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException(String.format("No session found for ID '%s'", sessionId));
        }
        File archive = Path.of((String)sessions.get(sessionId).get(INPUT__ARCHIVE)).toFile();
        String digest = (String)sessions.get(sessionId).get(INPUT__DIGEST);
        ValidationResult result = validator.upload(archive, digest);
        if (result.getUploadResult() != null && result.getUploadResult().getValidationUrl() != null) {
            // Record the report URL in the session to allow future report downloads.
            sessions.get(sessionId).put(INPUT__REPORT_URL, result.getUploadResult().getValidationUrl());
        }
        return toTAR(result, archive, digest, null);
    }

    /**
     * This operation is called through a validation step to make the report call on the backend validator.
     *
     * This is implemented here because we need to leverage the service's state (set up through the initialise operation).
     *
     * @param sessionId The session ID to refer to.
     * @return The validation report.
     */
    public TAR callReportForSession(String sessionId) {
        if (!sessions.containsKey(sessionId)) {
            throw new IllegalArgumentException(String.format("No session found for ID '%s'", sessionId));
        }
        String reportUrl = (String)sessions.get(sessionId).get(INPUT__REPORT_URL);
        TAR report;
        if (reportUrl == null) {
            // There has been no prior successful upload call to allow downloading the report.
            report = createEmptyReport();
            addReportItemError("Unable to validate archive's content", report.getReports().getInfoOrWarningOrError());
            report.getCounters().setNrOfErrors(report.getCounters().getNrOfErrors().add(BigInteger.ONE));
            report.setResult(TestResultType.FAILURE);
        } else {
            ValidationResult result = validator.validate(reportUrl);
            report = toTAR(result, null, null, reportUrl);
        }
        return report;
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

    /**
     * Remove any temp files on startup.
     */
    @PostConstruct
    public void cleanUp() {
        File tempFolder = Path.of(tmpFolder).toFile();
        File[] files = tempFolder.listFiles();
        if (files != null) {
            for (File file: files) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

}
