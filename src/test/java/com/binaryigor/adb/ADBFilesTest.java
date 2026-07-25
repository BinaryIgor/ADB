package com.binaryigor.adb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ADBFilesTest {

    private static final int HEADER_SIZE = 4;
    @TempDir
    Path testDir;

    @Test
    void resolvesCurrentDataFileWithExpectedNameFormat() throws Exception {
        var someBytes = TestUtils.randomBytes(8);
        for (int i = 0; i < 1000; i++) {
            var currentDataFile = ADBFiles.resolveCurrentDataFile(testDir, 8, 0);

            String expectedFileNameIndex;
            if (i < 10) {
                expectedFileNameIndex = "00" + i;
            } else if (i < 100) {
                expectedFileNameIndex = "0" + i;
            } else {
                expectedFileNameIndex = String.valueOf(i);
            }
            var expectedFileName = "data" + expectedFileNameIndex;

            assertThat(currentDataFile.getFileName().toString()).isEqualTo(expectedFileName);
            Files.write(currentDataFile, someBytes);
        }
    }

    @Test
    void returnsVariousEntriesSize() {
        for (int i = 0; i < 10; i++) {
            var keySize = TestUtils.randomNumber(5, 10);
            var valueSize = TestUtils.randomNumber(10, 100);
            var key = TestUtils.randomString(keySize);
            var value = TestUtils.randomBytes(valueSize);

            assertThat(ADBFiles.entrySize(key, value))
                    .isEqualTo(HEADER_SIZE + keySize + valueSize);
        }
    }
}
