package eu.europa.ec.itb.validator.eark.gitb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitb.tr.ObjectFactory;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;

/**
 * Configuration class responsible for creating the Spring beans required by the service.
 */
@Configuration
public class ServiceConfig {

    @Autowired
    Bus cxfBus;

    @Autowired
    ValidationServiceImpl validationServiceImplementation;

    @Autowired
    ProcessingServiceImpl processingServiceImplementation;

    /**
     * JSON serialiser/deserialiser.
     *
     * @return The object mapper.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * The CXF endpoint that will serve validation service calls.
     *
     * @return The endpoint.
     */
    @Bean
    public Endpoint validationService() {
        EndpointImpl endpoint = new EndpointImpl(cxfBus, validationServiceImplementation);
        endpoint.setServiceName(new QName("http://www.gitb.com/vs/v1/", "ValidationService"));
        endpoint.setEndpointName(new QName("http://www.gitb.com/vs/v1/", "ValidationServicePort"));
        endpoint.publish("/validation");
        return endpoint;
    }

    /**
     * The CXF endpoint that will serve processing service calls.
     *
     * @return The endpoint.
     */
    @Bean
    public Endpoint processingService() {
        EndpointImpl endpoint = new EndpointImpl(cxfBus, processingServiceImplementation);
        endpoint.setServiceName(new QName("http://www.gitb.com/ps/v1/", "ProcessingServiceService"));
        endpoint.setEndpointName(new QName("http://www.gitb.com/ps/v1/", "ProcessingServicePort"));
        endpoint.publish("/processing");
        return endpoint;
    }

    /**
     * The ObjectFactory used to construct GITB classes.
     *
     * @return The factory.
     */
    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

}
