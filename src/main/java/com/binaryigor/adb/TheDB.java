package com.binaryigor.adb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TheDB implements ADB {

    private static final Logger logger = LoggerFactory.getLogger(TheDB.class);
    private final Path dbDir;
    // TODO: limit size? How/where to keep it under control?
    private final Map<String, FileChannel> dataFileReadChannels = new ConcurrentHashMap<>();
    private final AtomicReference<FileOutputStream> currentDataFileOS = new AtomicReference<>();
    private final AtomicReference<String> currentDataFileId = new AtomicReference<>();
    private final AtomicLong currentDataFileSize = new AtomicLong();
    private final Lock currentDataFileRotationLock = new ReentrantLock();
    private final Index index;
    private final int dataFileSize;

    public TheDB(Path dbDir, Index index, int dataFileSize) {
        this.dbDir = dbDir;
        this.index = index;
        this.dataFileSize = dataFileSize;

        try {
            initCurrentDataFile();
            logger.info("DB initialized, current data file: {} with the size: {}", currentDataFileId.get(), currentDataFileSize.get());
            logger.info("Building the index...");
            index.build();
            logger.info("Index built; everything ready");
        } catch (Exception e) {
            logger.error("Failed to initialize ADB", e);
            throw new RuntimeException(e);
        }
    }

    private void initCurrentDataFile() throws Exception {
        var currentDataFilePath = ADBFiles.initDataFile(dbDir, dataFileSize);
        var currentDataFile = currentDataFilePath.toFile();
        currentDataFileOS.set(new FileOutputStream(currentDataFile, true));
        currentDataFileSize.set(Files.size(currentDataFilePath));
        currentDataFileId.set(currentDataFile.getName());

        // TODO: close when...
        var currentDataFileReadChannel = FileChannel.open(currentDataFilePath, StandardOpenOption.READ);
        dataFileReadChannels.put(currentDataFileId.get(), currentDataFileReadChannel);
    }

    @Override
    public void put(String key, byte[] value) {
        try {
            // intentionally, soft, not hard, file size guarantee - better performance
            if (ADBFiles.canWriteNextEntry(dataFileSize, currentDataFileSize.get(), key, value)) {
                var result = ADBFiles.writeNextEntry(currentDataFileOS.get(), key, value);
                // TODO: guarantee with index consistency - somehow; (a few retries and process exit maybe?)
                index.put(key, new Index.Entry(currentDataFileId.get(), result.offset()));
                currentDataFileSize.getAndAdd(result.entrySize());
            } else {
                try {
                    currentDataFileRotationLock.lock();
                    initCurrentDataFile();
                } finally {
                    currentDataFileRotationLock.unlock();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to put data of {} key into ADB", key, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String key) {
        put(key, new byte[0]);
    }

    @Override
    public Optional<byte[]> get(String key) {
        var indexEntryOpt = index.get(key);
        if (indexEntryOpt.isEmpty()) {
            return Optional.empty();
        }

        var indexEntry = indexEntryOpt.get();
        var fileId = indexEntry.fileId();
        var readChannel = dataFileReadChannels.computeIfAbsent(fileId, $ -> {
            try {
                return FileChannel.open(dbDir.resolve(fileId), StandardOpenOption.READ);
            } catch (Exception e) {
                throw new RuntimeException("Problem while opening read channel to the %s data file".formatted(fileId), e);
            }
        });

        var entry = ADBFiles.readEntry(readChannel, indexEntry.offset());
        return Optional.of(entry.value());
    }
}
