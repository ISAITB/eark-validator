package eu.europa.ec.itb.validator.eark.gitb;

import com.gitb.core.*;
import com.gitb.tr.TAR;
import com.gitb.tr.TestAssertionGroupReportsType;
import com.gitb.tr.TestResultType;
import com.gitb.tr.ValidationCounters;
import com.gitb.vs.ValidateRequest;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * BAse class holding common methods for service implementations.
 */
public abstract class BaseServiceImpl {

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
    TypedParameter createParameter(String name, String type, UsageEnumeration use, ConfigurationType kind, String description) {
        TypedParameter parameter =  new TypedParameter();
        parameter.setName(name);
        parameter.setType(type);
        parameter.setUse(use);
        parameter.setKind(kind);
        parameter.setDesc(description);
        return parameter;
    }

    /**
     * Get an parameter value and fail if it is not found.
     *
     * @param parameters The parameters to check.
     * @param inputName The input name.
     * @return The located value.
     */
    String getRequiredInput(List<AnyContent> parameters, String inputName) {
        List<AnyContent> inputs = getInput(parameters, inputName);
        if (inputs.size() != 1) {
            throw new IllegalArgumentException(String.format("This service expects one input to be provided named '%s'", inputName));
        }
        return inputs.get(0).getValue();
    }

    /**
     * Lookup a provided input from the received request parameters.
     *
     * @param parameters The request's parameters.
     * @param inputName The name of the input to lookup.
     * @return The inputs found to match the parameter name (not null).
     */
    List<AnyContent> getInput(List<AnyContent> parameters, String inputName) {
        List<AnyContent> inputs = new ArrayList<>();
        if (parameters != null) {
            for (AnyContent anInput: parameters) {
                if (inputName.equals(anInput.getName())) {
                    inputs.add(anInput);
                }
            }
        }
        return inputs;
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
    AnyContent createAnyContent(String name, String value, String type, ValueEmbeddingEnumeration embeddingMethod) {
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
    XMLGregorianCalendar getCurrentDate() {
        GregorianCalendar calendar = new GregorianCalendar();
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("Unable to construct data type factory for date", e);
        }
    }

    /**
     * Create an empty service report.
     *
     * @return The report.
     */
    TAR createEmptyReport() {
        TAR report = new TAR();
        report.setResult(TestResultType.SUCCESS);
        report.setDate(getCurrentDate());
        report.setContext(new AnyContent());
        report.setReports(new TestAssertionGroupReportsType());
        report.setCounters(new ValidationCounters());
        report.getCounters().setNrOfErrors(BigInteger.ZERO);
        report.getCounters().setNrOfWarnings(BigInteger.ZERO);
        report.getCounters().setNrOfAssertions(BigInteger.ZERO);
        return report;
    }

}
