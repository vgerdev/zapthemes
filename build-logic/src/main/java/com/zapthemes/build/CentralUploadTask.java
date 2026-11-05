package com.zapthemes.build;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Uploads a bundle to the Maven Central Portal publisher API as a {@code USER_MANAGED} deployment:
 * Central validates it, and nothing is released until someone presses Publish on
 * central.sonatype.com. Credentials are a Portal user token, from the {@code CENTRAL_TOKEN_USERNAME}
 * and {@code CENTRAL_TOKEN_PASSWORD} environment variables.
 */
@DisableCachingByDefault(because = "Uploads to a remote service")
public abstract class CentralUploadTask extends DefaultTask {

    static final String UPLOAD_URL = "https://central.sonatype.com/api/v1/publisher/upload";

    @InputFile
    public abstract RegularFileProperty getBundle();

    @Input
    public abstract Property<String> getDeploymentName();

    @Internal
    @Optional
    public abstract Property<String> getUsername();

    @Internal
    @Optional
    public abstract Property<String> getPassword();

    @TaskAction
    public void upload() throws IOException, InterruptedException {
        if (!getUsername().isPresent() || !getPassword().isPresent()) {
            throw new GradleException("Set CENTRAL_TOKEN_USERNAME and CENTRAL_TOKEN_PASSWORD to a Central Portal user token.");
        }
        Path bundle = getBundle().get().getAsFile().toPath();
        String boundary = "zapthemes-" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"bundle\"; filename=\"" + bundle.getFileName() + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        String token = Base64.getEncoder().encodeToString(
                (getUsername().get() + ":" + getPassword().get()).getBytes(StandardCharsets.UTF_8));

        URI uri = URI.create(UPLOAD_URL + "?publishingType=USER_MANAGED&name="
                + URLEncoder.encode(getDeploymentName().get(), StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, Files.readAllBytes(bundle), tail)))
                .build();
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        if (response.statusCode() / 100 != 2) {
            throw new GradleException("Central upload failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        getLogger().lifecycle("Uploaded {} as deployment {}; publish it at https://central.sonatype.com/publishing/deployments",
                bundle.getFileName(), response.body().trim());
    }
}
