package com.binaryigor.adb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class TheDB implements ADB {

    private static final String DATA_FILE_NAME = "data";
    private static final Logger logger = LoggerFactory.getLogger(TheDB.class);
    private final Path rootDir;
    // probably can use just that, instead of currentDataFileOS
    private final FileChannel currentDataFileChannel;
    private final FileOutputStream currentDataFileOS;
    private final Index index;
    private final int dataFileSize;
    private final AtomicLong currentDataFileSize;

    public TheDB(Path rootDir, Index index, int dataFileSize) {
        this.rootDir = rootDir;
        this.index = index;
        this.dataFileSize = dataFileSize;

        try {
            Files.createDirectories(rootDir);

            var currentDataFilePath = latestDataFilePath()
                    .orElseGet(() -> Path.of(rootDir.toString(), "%s_000".formatted(DATA_FILE_NAME)));
            var currentDataFile = currentDataFilePath.toFile();

            this.currentDataFileOS = new FileOutputStream(currentDataFile, true);
            this.currentDataFileChannel = FileChannel.open(currentDataFilePath,
                    StandardOpenOption.APPEND, StandardOpenOption.READ);


            this.currentDataFileSize = new AtomicLong(currentDataFile.getTotalSpace());

            logger.info("DB initialized, current data file: {}", currentDataFile);
        } catch (Exception e) {
            logger.error("Failed to initialize DB", e);
            throw new RuntimeException(e);
        }
    }

    private Optional<Path> latestDataFilePath() throws Exception {
        try (var files = Files.list(rootDir)) {
            var dataFiles = files.filter(p -> p.toString().contains(DATA_FILE_NAME))
                    .sorted()
                    .toList();
            return dataFiles.isEmpty() ? Optional.empty() : Optional.of(dataFiles.getLast());
        }
    }

    @Override
    public void put(String key, byte[] value) {
        try {
            ADBFiles.writeNextEntry(currentDataFileOS, key, value);
        } catch (Exception e) {
            logger.error("Failed to put data of {} key into ADB", key, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        var indexEntry = index.get(key);
        if (indexEntry.isEmpty()) {
            return Optional.empty();
        }

        // TODO: support multiple files, do not ignore offset id!
        var entry = ADBFiles.readAtOffsetEntry(currentDataFileChannel, indexEntry.get().offset());
        return Optional.of(entry.value());
    }
}
