package com.abubakr.ecommerceplatform.service;

import com.abubakr.ecommerceplatform.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    // Keys are random UUIDs, so a stored file never changes and browsers may cache it for a year.
    private static final String IMMUTABLE_CACHE = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final StorageProperties properties;

    public String upload(String key, byte[] content, String contentType) {
        s3Client.putObject(request -> request
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(IMMUTABLE_CACHE),
                RequestBody.fromBytes(content));
        return publicUrl(key);
    }

    public void delete(String key) {
        try {
            s3Client.deleteObject(request -> request.bucket(properties.bucket()).key(key));
        } catch (SdkException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    public String publicUrl(String key) {
        return properties.publicUrl() + "/" + key;
    }

    // Development convenience only; in production the bucket and its policy are created by infrastructure.
    @EventListener(ApplicationReadyEvent.class)
    public void createBucketIfMissing() {
        if (!properties.createBucket()) {
            return;
        }
        String bucket = properties.bucket();
        try {
            s3Client.headBucket(request -> request.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(request -> request.bucket(bucket));
            s3Client.putBucketPolicy(request -> request.bucket(bucket).policy(publicReadPolicy(bucket)));
            log.info("Created storage bucket '{}' with public read access", bucket);
        } catch (SdkException e) {
            log.warn("Object storage is not reachable; image uploads will fail until it is: {}", e.getMessage());
        }
    }

    private static String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }""".formatted(bucket);
    }
}
