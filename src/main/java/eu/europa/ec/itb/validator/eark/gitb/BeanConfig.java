package eu.europa.ec.itb.validator.eark.gitb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitb.tr.ObjectFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class responsible for creating Spring beans.
 */
@Configuration
public class BeanConfig {

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
     * The ObjectFactory used to construct GITB classes.
     *
     * @return The factory.
     */
    @Bean
    public ObjectFactory objectFactory() {
        return new ObjectFactory();
    }

}
