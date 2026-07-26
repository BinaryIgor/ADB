package com.binaryigor.adb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ADBFilesTest {

    private static final int HEADER_SIZE = 4;
    @TempDir
    Path testDir;

    @Test
    void resolvesCurrentDataFileWithExpectedNameFormat() throws Exception {
        var someBytes = TestUtils.randomBytes(8);
        for (int i = 0; i < 100; i++) {
            var currentDataFile = ADBFiles.resolveCurrentDataFile(testDir, 8, 0);

            var expectedFileName = ADBFiles.dataFileName(i);

            assertThat(currentDataFile.getFileName().toString()).isEqualTo(expectedFileName);
            Files.write(currentDataFile, someBytes);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0, data000000000000000000",
            "3, data000000000000000003",
            "10, data000000000000000010",
            "22, data000000000000000022",
            "4003000004400000022, data4003000004400000022"
    })
    void returnsNumberedDataFileName(long number, String expectedFileName) {
        assertThat(ADBFiles.dataFileName(number)).isEqualTo(expectedFileName);
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

    @Test
    void readsAllIndexEntriesSequentially() {
        // a bit non-standard numbering to see whether sorting at the edge of single to two-digit numbers work
        var file0 = ADBFiles.dataFileName(1);
        var file1 = ADBFiles.dataFileName(11);
        var file2 = ADBFiles.dataFileName(101);

        var valuesSize = TestUtils.randomNumber(4, 16);
        var file0Data = List.of(new DataFileEntry("A", TestUtils.randomBytes(valuesSize)),
                new DataFileEntry("B", TestUtils.randomBytes(valuesSize)),
                new DataFileEntry("C", TestUtils.randomBytes(valuesSize)));
        var file1Data = List.of(new DataFileEntry("A", TestUtils.randomBytes(valuesSize)),
                new DataFileEntry("B", TestUtils.randomBytes(valuesSize)));
        var file2Data = List.of(new DataFileEntry("A", TestUtils.randomBytes(valuesSize)));

        prepareDataFile(file0, file0Data);
        prepareDataFile(file1, file1Data);
        prepareDataFile(file2, file2Data);

        // full entry size - key + data
        var fullEntrySize = HEADER_SIZE + 1 + valuesSize;
        var expectedIndexEntries = List.of(
                new ADBFiles.IndexEntry(file0, "A", 0),
                new ADBFiles.IndexEntry(file0, "B", fullEntrySize),
                new ADBFiles.IndexEntry(file0, "C", 2L * fullEntrySize),
                new ADBFiles.IndexEntry(file1, "A", 0),
                new ADBFiles.IndexEntry(file1, "B", fullEntrySize),
                new ADBFiles.IndexEntry(file2, "A", 0)
        );

        assertThat(ADBFiles.readAllIndexEntriesSequentially(testDir))
                .isEqualTo(expectedIndexEntries);
    }

    @Test
    void readsAllFilesMetadata() {
        var file0 = ADBFiles.dataFileName(0);
        var file1 = ADBFiles.dataFileName(2);
        var file2 = ADBFiles.dataFileName(11);

        var file0EntryA0Bytes = TestUtils.randomBytes(2);
        var file0EntryB0Bytes = TestUtils.randomBytes(2);
        var file0EntryCBytes = TestUtils.randomBytes(2);
        var file0EntryA1Bytes = TestUtils.randomBytes(2);
        prepareDataFile(file0, List.of(
                new DataFileEntry("A", TestUtils.randomBytes(2)),
                new DataFileEntry("B", TestUtils.randomBytes(3)),
                new DataFileEntry("C", TestUtils.randomBytes(3)),
                new DataFileEntry("A", TestUtils.randomBytes(4))
        ));
        var file0EntryA0Size = ADBFiles.entrySize("A", )
        var file0MeanEntrySize = (2 + 3 + 4 + 4) / 4;

        var latestKeyFileIdResolver = new TestLatestKeyVersionIdResolver()
                .addLatestKeyFileId("C", file2);


        var expectedFilesMetadata = List.of(
                new ADBFiles.FileMetadata(file0, 4, 2, 2, file0MeanEntrySize,2, 4)
        );

        assertThat(ADBFiles.readAllFilesMetadata(testDir, latestKeyFileIdResolver))
                .isEqualTo(expectedFilesMetadata);
    }

    private void prepareDataFile(String dataFile, List<DataFileEntry> data) {
        var file = testDir.resolve(dataFile).toFile();
        try (var os = new FileOutputStream(file)) {
            data.forEach(e -> ADBFiles.writeNextEntry(os, e.key, e.value));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    record DataFileEntry(String key, byte[] value) {
    }

    static class TestLatestKeyVersionIdResolver implements ADBFiles.LatestKeyVersionFileIdResolver {

        private final Map<String, String> keyFileIds = new HashMap<>();

        public TestLatestKeyVersionIdResolver addLatestKeyFileId(String key, String fileId) {
            keyFileIds.put(key, fileId);
            return this;
        }

        @Override
        public Optional<String> resolve(String key) {
            return Optional.ofNullable(keyFileIds.get(key));
        }
    }
}
