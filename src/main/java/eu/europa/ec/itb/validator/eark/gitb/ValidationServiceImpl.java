package eu.europa.ec.itb.validator.eark.gitb;

import com.gitb.core.*;
import com.gitb.tr.TAR;
import com.gitb.vs.Void;
import com.gitb.vs.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring component that realises the validation service.
 */
@Component
public class ValidationServiceImpl extends BaseServiceImpl implements ValidationService {

    /** The name of the input parameter for the operation to perform. */
    public static final String INPUT__OPERATION = "operation";
    /** The name of the input parameter for the validator session. */
    public static final String INPUT__SESSION = "session";
    /** Operation instructing the validator to only upload the archive and do the SHA check. */
    public static final String OPERATION__UPLOAD = "upload";
    /** Operation instructing the validator to only get a validation report from a provided URL. */
    public static final String OPERATION__REPORT = "report";

    @Value("${service.id}")
    private String serviceId;

    @Value("${service.version}")
    private String serviceVersion;

    @Autowired
    private ProcessingServiceImpl processingService;

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
        response.getModule().getInputs().getParam().add(createParameter(INPUT__SESSION, "string", UsageEnumeration.R, ConfigurationType.SIMPLE, "The validator session to refer to."));
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
        if (!OPERATION__REPORT.equals(operation) && !OPERATION__UPLOAD.equals(operation)) {
            throw new IllegalArgumentException(String.format("Unexpected value provided for input '%s'", INPUT__OPERATION));
        }
        String validatorSession = getRequiredInput(parameters.getInput(), INPUT__SESSION);
        TAR validationReport;
        if (OPERATION__UPLOAD.equals(operation)) {
            validationReport = processingService.callUploadForSession(validatorSession);
        } else { // OPERATION__REPORT
            validationReport = processingService.callReportForSession(validatorSession);
        }
        result.setReport(validationReport);
        return result;
    }

}
