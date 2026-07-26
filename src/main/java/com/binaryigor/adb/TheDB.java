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

public class TheDB implements ADB, ADBFiles.LatestKeyVersionFileIdResolver {

    private static final Logger logger = LoggerFactory.getLogger(TheDB.class);
    private final Path dbDir;
    // TODO: limit size? How/where to keep it under control?
    private final Map<String, FileChannel> dataFileReadChannels = new ConcurrentHashMap<>();
    private final AtomicReference<FileOutputStream> currentDataFileOS = new AtomicReference<>();
    private final AtomicReference<String> currentDataFileId = new AtomicReference<>();
    private final AtomicLong currentDataFileSize = new AtomicLong();
    private final Lock currentDataFileRotationLock = new ReentrantLock();
    private final InMemoryIndex index;
    private final int dataFileSize;

    // TODO: data file size validation
    public TheDB(Path dbDir, int dataFileSize) {
        this.dbDir = dbDir;
        this.index = new InMemoryIndex();
        this.dataFileSize = dataFileSize;
    }

    @Override
    public void init() {
        try {
            if (!Files.exists(dbDir)) {
                Files.createDirectory(dbDir);
            }
            initCurrentDataFile(0);
            logger.info("DB initialized, current data file: {} with the size: {}", currentDataFileId.get(), currentDataFileSize.get());
            logger.info("Building the index...");
            index.build(dbDir);
            logger.info("Index built; everything ready");
        } catch (Exception e) {
            logger.error("Failed to initialize ADB", e);
            throw new RuntimeException(e);
        }
    }

    private void initCurrentDataFile(int neededFreeSpace) throws Exception {
        var currentDataFilePath = ADBFiles.resolveCurrentDataFile(dbDir, dataFileSize, neededFreeSpace);
        var currentDataFile = currentDataFilePath.toFile();
        currentDataFileOS.set(new FileOutputStream(currentDataFile, true));
        currentDataFileSize.set(Files.size(currentDataFilePath));
        currentDataFileId.set(currentDataFile.getName());

        // TODO: close when not read from for N minutes/seconds
        var currentDataFileReadChannel = FileChannel.open(currentDataFilePath, StandardOpenOption.READ);
        dataFileReadChannels.put(currentDataFileId.get(), currentDataFileReadChannel);
    }

    // TODO: maybe forgot to initialize err in the exception msg?
    @Override
    public void put(String key, byte[] value) {
        try {
            currentDataFileRotationLock.lock();
            var entrySize = ADBFiles.entrySize(key, value);
            // intentionally, soft, not hard, file size guarantee - better performance
            if (!ADBFiles.canWriteNextEntry(dataFileSize, currentDataFileSize.get(), entrySize)) {
                initCurrentDataFile(entrySize);
            }
        } catch (Exception e) {
            logger.error("Failed to init new current data file", e);
            throw new RuntimeException(e);
        } finally {
            currentDataFileRotationLock.unlock();
        }
        var result = ADBFiles.writeNextEntry(currentDataFileOS.get(), key, value);
        currentDataFileSize.getAndAdd(result.entrySize());
        if (value.length > 0) {
            index.put(key, new InMemoryIndex.Entry(currentDataFileId.get(), result.offset()));
        } else {
            index.delete(key);
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

    @Override
    public Optional<String> resolve(String key) {
        return index.get(key).map(InMemoryIndex.Entry::fileId);
    }

    private static class InMemoryIndex {
        private final Map<String, Entry> index = new ConcurrentHashMap<>();

        void build(Path dbDir) {
            ADBFiles.readAllIndexEntriesSequentially(dbDir)
                    .forEach(e -> index.put(e.key(), new Entry(e.fileId(), e.offset())));
        }

        void put(String key, Entry entry) {
            index.put(key, entry);
        }

        void delete(String key) {
            index.remove(key);
        }

        Optional<Entry> get(String key) {
            return Optional.ofNullable(index.get(key));
        }

        record Entry(String fileId, long offset) {
        }
    }
}
