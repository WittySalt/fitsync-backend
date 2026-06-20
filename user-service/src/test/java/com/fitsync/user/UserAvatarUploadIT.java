package com.fitsync.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.user.repository.UserProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "MINIO_INTEGRATION_TESTS", matches = "true")
class UserAvatarUploadIT {

    private static final String BUCKET = "fitsync-media";
    private static final int MINIO_PORT = 9000;
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    @Container
    private static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2024-07-16T23-46-41Z"))
                    .withEnv("MINIO_ROOT_USER", "fitsync")
                    .withEnv("MINIO_ROOT_PASSWORD", "fitsync-password")
                    .withCommand("server /data --console-address :9001")
                    .withExposedPorts(MINIO_PORT);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("fitsync.media.endpoint", UserAvatarUploadIT::mediaEndpoint);
        registry.add("fitsync.media.region", () -> "us-east-1");
        registry.add("fitsync.media.access-key", () -> "fitsync");
        registry.add("fitsync.media.secret-key", () -> "fitsync-password");
        registry.add("fitsync.media.bucket", () -> BUCKET);
        registry.add("fitsync.media.public-base-url", () -> mediaEndpoint() + "/" + BUCKET);
    }

    @BeforeEach
    void ensureBucket() {
        boolean bucketExists = s3Client.listBuckets().buckets().stream()
                .anyMatch(bucket -> BUCKET.equals(bucket.name()));
        if (!bucketExists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @Test
    void uploadAvatarStoresObjectInMinioAndUpdatesProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId, "avatar@example.com", "Avatar User");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        MvcResult result = mockMvc.perform(multipart("/api/v1/users/me/profile/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Avatar User"))
                .andReturn();

        String avatarUrl = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("avatarUrl")
                .asText();
        String publicBaseUrl = mediaEndpoint() + "/" + BUCKET;
        assertThat(avatarUrl)
                .startsWith(publicBaseUrl + "/avatars/" + userId + "/")
                .endsWith(".png");
        assertThat(userProfileRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(profile -> profile.getAvatarUrl())
                .isEqualTo(avatarUrl);

        String objectKey = URI.create(avatarUrl).getPath().substring(("/" + BUCKET + "/").length());
        var object = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET)
                .key(objectKey)
                .build());
        assertThat(object.contentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(object.contentLength()).isEqualTo(file.getSize());

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(avatarUrl));
    }

    @Test
    void uploadAvatarRejectsNonImageContentType() throws Exception {
        String token = tokenFor(UUID.randomUUID(), "plain@example.com", "Plain User");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/users/me/profile/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Avatar file must be image/png, image/jpeg, or image/webp"));
    }

    @Test
    void uploadAvatarRejectsFilesLargerThanTwoMegabytes() throws Exception {
        String token = tokenFor(UUID.randomUUID(), "large@example.com", "Large User");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[(int) MAX_AVATAR_BYTES + 1]);

        mockMvc.perform(multipart("/api/v1/users/me/profile/avatar")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Avatar file size must be 2MB or less"));
    }

    @Test
    void uploadAvatarRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.webp",
                "image/webp",
                new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/v1/users/me/profile/avatar").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    private static String mediaEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(MINIO_PORT);
    }

    private String tokenFor(UUID userId, String email, String displayName) {
        return Jwts.builder()
                .claims(Map.of(
                        "userId", userId.toString(),
                        "displayName", displayName,
                        "role", "USER"))
                .subject(email)
                .signWith(Keys.hmacShaKeyFor(
                        "test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
