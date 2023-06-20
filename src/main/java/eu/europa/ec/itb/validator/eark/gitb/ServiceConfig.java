package eu.europa.ec.itb.validator.eark.gitb;

import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.xml.namespace.QName;

/**
 * Configuration class responsible for creating the Spring beans required by the service.
 */
@Configuration
public class ServiceConfig {

    @Autowired
    Bus cxfBus;

    @Autowired
    ValidationServiceImpl validationServiceImplementation;

    /**
     * The CXF endpoint that will serve validation service calls.
     *
     * @return The endpoint.
     */
    @Bean
    public EndpointImpl validationService() {
        EndpointImpl endpoint = new EndpointImpl(cxfBus, validationServiceImplementation);
        endpoint.setServiceName(new QName("http://www.gitb.com/vs/v1/", "ValidationService"));
        endpoint.setEndpointName(new QName("http://www.gitb.com/vs/v1/", "ValidationServicePort"));
        endpoint.publish("/validation");
        return endpoint;
    }

}
