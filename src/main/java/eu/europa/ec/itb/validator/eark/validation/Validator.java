package eu.europa.ec.itb.validator.eark.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Class that implements the validator's logic.
 *
 * This implementation forwards the provided archive and digest hash to the backend validator's REST API.
 */
@Component
public class Validator {

    @Value("${validator.backendEndpoint}")
    private String backendEndpoint;

    @Value("${validator.forceHttps:false}")
    private boolean forceHttps;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Validate the input.
     *
     * @param archive The archive to validate.
     * @param digest The archive's hash value.
     * @return The result of the validation.
     */
    public ValidationResult validate(File archive, String digest) {
        UploadResult uploadResult = uploadArchive(archive, digest);
        ValidationReport report = null;
        if (uploadResult.getValidationUrl() != null && !uploadResult.getValidationUrl().isBlank()) {
            report = getValidationReport(uploadResult.getValidationUrl());
        }
        return new ValidationResult(uploadResult, report);
    }

    /**
     * Make the second call to get the validation report.
     *
     * @param reportUrl The URL to call.
     * @return The report.
     */
    private ValidationReport getValidationReport(String reportUrl) {
        if (forceHttps && reportUrl.startsWith("http://")) {
            reportUrl = "https" + reportUrl.substring(4);
        }
        HttpGet reportRequest = new HttpGet(reportUrl);
        HttpClient client = HttpClientBuilder.create().build();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            HttpResponse response = client.execute(reportRequest);
            response.getEntity().writeTo(bos);
            String receivedResult = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return objectMapper.readValue(receivedResult, ValidationReport.class);
        } catch (IOException e) {
            throw new IllegalStateException("An error occurred while downloading the archive's validation report", e);
        }
    }

    /**
     * Make the first call to upload the archive to validate.
     *
     * @param archive The archive.
     * @param digest The archive's digest.
     * @return The result of the call.
     */
    private UploadResult uploadArchive(File archive, String digest) {
        HttpEntity uploadEntity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.RFC6532)
                .addPart("package", new FileBody(archive, ContentType.DEFAULT_BINARY))
                .addPart("digest", new StringBody(digest, ContentType.MULTIPART_FORM_DATA))
                .build();
        HttpPost uploadRequest = new HttpPost(backendEndpoint);
        uploadRequest.setEntity(uploadEntity);
        HttpClient client = HttpClientBuilder.create().build();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            HttpResponse response = client.execute(uploadRequest);
            response.getEntity().writeTo(bos);
            String receivedResult = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            return objectMapper.readValue(receivedResult, UploadResult.class);
        } catch (IOException e) {
            throw new IllegalStateException("An error occurred while uploading the archive for validation", e);
        }
    }

}
