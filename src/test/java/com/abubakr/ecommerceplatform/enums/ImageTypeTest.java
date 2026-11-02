package com.abubakr.ecommerceplatform.enums;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    @Test
    void detectsJpegPngAndWebpFromTheirBytes() {
        assertThat(ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00))).contains(ImageType.PNG);
        assertThat(ImageType.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V'))).contains(ImageType.WEBP);
    }

    @Test
    void rejectsOtherFormatsEvenIfTheyClaimToBeImages() {
        assertThat(ImageType.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect(bytes('M', 'Z', 0x90, 0x00, 0x03))).isEmpty();                     // Windows .exe
        assertThat(ImageType.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'))).isEmpty(); // .wav is RIFF too
        assertThat(ImageType.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void rejectsEmptyOrTruncatedInput() {
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
