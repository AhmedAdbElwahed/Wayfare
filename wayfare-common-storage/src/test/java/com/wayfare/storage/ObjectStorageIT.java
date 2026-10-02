package com.wayfare.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ObjectStorageIT {

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("quay.io/minio/minio:latest")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "test").withEnv("MINIO_ROOT_PASSWORD", "test-secret")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static ObjectStorage storage;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void init() throws Exception {
        String url = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        storage = new ObjectStorage(new StorageProperties(url, url, "test", "test-secret", null, null, null));
        storage.ensureBuckets();
    }

    private int put(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void presignedUploadThenStatThenDownloadThenDelete() throws Exception {
        String key = "drivers/abc/documents/license.pdf";
        assertThat(storage.stat(storage.documentsBucket(), key)).isEmpty();

        var upload = storage.presignUpload(storage.documentsBucket(), key, Duration.ofMinutes(5));
        assertThat(put(upload.uploadUrl(), "hello")).isEqualTo(200);

        var stat = storage.stat(storage.documentsBucket(), key);
        assertThat(stat).isPresent();
        assertThat(stat.get().size()).isEqualTo(5);

        String download = storage.presignDownload(storage.documentsBucket(), key, Duration.ofMinutes(5));
        assertThat(get(download)).isEqualTo(200);

        storage.delete(storage.documentsBucket(), key);
        assertThat(storage.stat(storage.documentsBucket(), key)).isEmpty();
    }

    @Test
    void documentsAreNotPubliclyReadableButMediaIs() throws Exception {
        String docKey = "drivers/x/documents/a.pdf";
        String mediaKey = "riders/x/photo.jpg";
        put(storage.presignUpload(storage.documentsBucket(), docKey, Duration.ofMinutes(5)).uploadUrl(), "d");
        put(storage.presignUpload(storage.mediaBucket(), mediaKey, Duration.ofMinutes(5)).uploadUrl(), "m");

        assertThat(get(storage.publicUrl(mediaKey))).isEqualTo(200);
        String docDirect = storage.publicUrl(docKey).replace("/" + storage.mediaBucket() + "/", "/" + storage.documentsBucket() + "/");
        assertThat(get(docDirect)).isEqualTo(403);
    }

    private static final java.util.Set<String> TYPES = java.util.Set.of("application/pdf");

    private void putTyped(String url, String body, String type) throws Exception {
        http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", type)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void requireUploadedAcceptsGoodFileAndRejectsBadOnes() throws Exception {
        String prefix = "drivers/u1/documents/";
        String good = ObjectStorage.newKey(prefix, "application/pdf", TYPES);
        putTyped(storage.presignUpload(storage.documentsBucket(), good, Duration.ofMinutes(5)).uploadUrl(), "pdf", "application/pdf");
        assertThat(storage.requireUploaded(storage.documentsBucket(), good, prefix, 1024, TYPES).size()).isEqualTo(3);

        // someone else's prefix
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                storage.requireUploaded(storage.documentsBucket(), good, "drivers/other/", 1024, TYPES))
                .isInstanceOf(InvalidUploadException.class);
        // never uploaded
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                storage.requireUploaded(storage.documentsBucket(), prefix + "nope.pdf", prefix, 1024, TYPES))
                .isInstanceOf(InvalidUploadException.class);
        // wrong content type: rejected and removed
        String bad = prefix + "bad.pdf";
        putTyped(storage.presignUpload(storage.documentsBucket(), bad, Duration.ofMinutes(5)).uploadUrl(), "x", "text/html");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                storage.requireUploaded(storage.documentsBucket(), bad, prefix, 1024, TYPES))
                .isInstanceOf(InvalidUploadException.class);
        assertThat(storage.stat(storage.documentsBucket(), bad)).isEmpty();
        // too large
        String big = prefix + "big.pdf";
        putTyped(storage.presignUpload(storage.documentsBucket(), big, Duration.ofMinutes(5)).uploadUrl(), "0123456789", "application/pdf");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                storage.requireUploaded(storage.documentsBucket(), big, prefix, 5, TYPES))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void newKeyRejectsUnsupportedTypes() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ObjectStorage.newKey("p/", "text/html", java.util.Set.of("text/html", "application/pdf")))
                .isInstanceOf(InvalidUploadException.class);
    }
}
