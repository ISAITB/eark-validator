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
public class ValidationServiceConfig {

    @Autowired
    Bus cxfBus;

    @Autowired
    ValidationServiceImpl validationServiceImplementation;

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
     * The CXF endpoint that will serve service calls.
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
     * The ObjectFactory used to construct GITB classes.
     *
     * @return The factory.
     */
    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

}
