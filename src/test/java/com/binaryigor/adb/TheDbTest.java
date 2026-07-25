package com.binaryigor.adb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TheDbTest {

    // 1 MB
    static final int DATA_FILE_SIZE = 1024 * 1024;
    @TempDir
    Path rootDir;
    Path dbDir;
    TheDB db;

    @BeforeEach
    void setup() {
        dbDir = rootDir.resolve("adb");
        db = newDB();
    }

    private TheDB newDB() {
        return new TheDB(dbDir, DATA_FILE_SIZE);
    }

    @Test
    void initializesDbDirWith000DataFile() {
        assertThat(Files.exists(dbDir)).isFalse();
        assertThat(Files.exists(dbDir.resolve("data000"))).isFalse();

        db.init();

        assertThat(Files.exists(dbDir)).isTrue();
        assertThat(Files.isDirectory(dbDir)).isTrue();
        assertThat(Files.exists(dbDir.resolve("data000"))).isTrue();
    }

    @Test
    void putsVariousKeysAndValues() {
        var keyValues = randomKeysAndValues(10);

        db.init();
        keyValues.forEach((k, v) -> db.put(k, v));

        keyValues.forEach((k, v1) -> {
            assertThat(db.get(k))
                    .isPresent()
                    .get()
                    .isEqualTo(v1);

            var v2 = TestUtils.randomBytes();
            db.put(k, v2);

            assertThat(db.get(k))
                    .isPresent()
                    .get()
                    .isEqualTo(v2);
        });
    }

    @Test
    void deletesVariousKeys() {
        var keysValues = randomKeysAndValues(10);

        db.init();
        keysValues.forEach((k, v) -> db.put(k, v));

        keysValues.keySet().forEach(k -> {
            assertThat(db.get(k)).isPresent();

            db.delete(k);

            assertThat(db.get(k)).isEmpty();
        });
    }

    @Test
    void buildsIndexOnInit() {
        var keyValues = randomKeysAndValues(10);
        db.init();
        keyValues.forEach((k, v) -> db.put(k, v));

        var anotherDb = newDB();
        keyValues.forEach((k, _) ->
                assertThat(anotherDb.get(k))
                        .isEmpty());

        anotherDb.init();

        keyValues.forEach((k, v) -> assertThat(anotherDb.get(k))
                .isPresent()
                .get()
                .isEqualTo(v));
    }

    @Test
    void createsNextDataFilesOnceSizeLimitIsReachedConcurrently() throws Exception {
        var dataFile0 = dbDir.resolve("data000");
        var dataFile1 = dbDir.resolve("data001");
        var dataFile2 = dbDir.resolve("data002");

        db.init();

        Runnable putKeysOnMultipleThreadsToTriggerNextDataFileRotation = () -> {
            var dataSize = 0;
            var bytes = TestUtils.randomBytes(3 * 1024);
            try (var executor = Executors.newFixedThreadPool(50)) {
                // soft limit guarantees, especially with high concurrency - that's why the multiplication
                while ((1.25 * DATA_FILE_SIZE) > dataSize) {
                    var key = TestUtils.randomString();
                    executor.submit(() -> db.put(key, bytes));
                    dataSize += ADBFiles.entrySize(key, bytes);
                }
            }
        };

        assertThat(Files.exists(dataFile0)).isTrue();
        assertThat(Files.exists(dataFile1)).isFalse();
        assertThat(Files.exists(dataFile2)).isFalse();

        putKeysOnMultipleThreadsToTriggerNextDataFileRotation.run();

        assertThat(Files.exists(dataFile0)).isTrue();
        assertThat(Files.exists(dataFile1)).isTrue();
        assertThat(Files.exists(dataFile2)).isFalse();

        putKeysOnMultipleThreadsToTriggerNextDataFileRotation.run();

        assertThat(Files.exists(dataFile0)).isTrue();
        assertThat(Files.exists(dataFile1)).isTrue();
        assertThat(Files.exists(dataFile2)).isTrue();

        assertThat(Files.size(dataFile0)).isGreaterThanOrEqualTo(DATA_FILE_SIZE);
        assertThat(Files.size(dataFile1)).isGreaterThanOrEqualTo(DATA_FILE_SIZE);
        assertThat(Files.size(dataFile2)).isGreaterThan(0);
    }

    @Test
    void readsKeysConcurrently() {
        db.init();
        var keyValues = randomKeysAndValues(25);
        keyValues.forEach((k, v) -> db.put(k, v));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var keys = keyValues.keySet().stream().toList();
            for (int i = 0; i < 250; i++) {
                executor.submit(() -> {
                    var key = TestUtils.randomElement(keys);
                    assertThat(db.get(key))
                            .isPresent()
                            .get()
                            .isEqualTo(keyValues.get(key));
                });
            }
        }
    }

    private Map<String, byte[]> randomKeysAndValues(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> Map.entry(TestUtils.randomString(), TestUtils.randomBytes()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
