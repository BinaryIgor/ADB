package com.binaryigor.adb;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ADBFiles {

    private static final String DATA_FILE_NAME = "data";
    private static final int DATA_FILE_HEADER_SIZE = 4;
    private static final int NO_SIGN_BYTE_MASK = 0xFF;
    private static final int TOMBSTONE_VALUE_LENGTH = 0;

    public static Path resolveCurrentDataFile(Path dbDir, int wantedFileSize, int neededFreeSpace) {
        try {
            var latestDataFileOpt = resolveLatestDataFilePath(dbDir);
            if (latestDataFileOpt.isEmpty()) {
                return dbDir.resolve(DATA_FILE_NAME + "000");
            }
            var latestDataFile = latestDataFileOpt.get();
            if (wantedFileSize > (Files.size(latestDataFile) + neededFreeSpace)) {
                return latestDataFile;
            }
            var latestDataFileNumber = Integer.parseInt(latestDataFile.getFileName().toString().replace(DATA_FILE_NAME, ""));
            return dbDir.resolve(DATA_FILE_NAME + "%03d".formatted(latestDataFileNumber + 1));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ADB data file", e);
        }
    }

    private static Optional<Path> resolveLatestDataFilePath(Path dbDir) throws Exception {
        try (var files = Files.list(dbDir)) {
            var dataFiles = files.filter(p -> p.toString().contains(DATA_FILE_NAME))
                    .sorted()
                    .toList();
            return dataFiles.isEmpty() ? Optional.empty() : Optional.of(dataFiles.getLast());
        }
    }

    public static List<IndexEntry> readAllIndexEntries(Path dbDir) {
        if (!Files.isDirectory(dbDir)) {
            return List.of();
        }

        try (var files = Files.list(dbDir)) {
            return files.filter(fp -> fp.toString().contains(DATA_FILE_NAME))
                    .flatMap(fp -> readIndexEntries(fp).stream()).toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read index entries from %s db dir".formatted(dbDir), e);
        }
    }

    private static List<IndexEntry> readIndexEntries(Path dbDir) {
        var entries = new ArrayList<IndexEntry>();

        try (var channel = FileChannel.open(dbDir, StandardOpenOption.READ)) {
            var offset = 0;
            while (offset < channel.size()) {
                var entry = readEntry(channel, offset, true);
                offset += entry.totalSize();

                var indexEntry = new IndexEntry(dbDir.getFileName().toString(), entry.key(), entry.offset());
                entries.add(indexEntry);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read index entries from %s db file".formatted(dbDir), e);
        }

        return entries;
    }

    private static InternalEntry readEntry(FileChannel fileChannel, long offset, boolean skipValue) {
        try {
            // TODO: configurable maybe?
            var headerBuffer = ByteBuffer.allocate(DATA_FILE_HEADER_SIZE);
            fileChannel.read(headerBuffer, offset);
            headerBuffer.flip();

            var keyLength = headerBuffer.get() & NO_SIGN_BYTE_MASK;

            // 2^32 - 16 777 216 max value size
            var valueLengthByte2 = headerBuffer.get();
            var valueLengthByte1 = headerBuffer.get();
            var valueLengthByte0 = headerBuffer.get();

            var valueLength = ((valueLengthByte2 & NO_SIGN_BYTE_MASK) << 16) |
                    ((valueLengthByte1 & NO_SIGN_BYTE_MASK) << 8) |
                    (valueLengthByte0 & NO_SIGN_BYTE_MASK);

            var afterHeaderOffset = offset + DATA_FILE_HEADER_SIZE;

            var keyBuffer = ByteBuffer.allocate(keyLength);
            fileChannel.read(keyBuffer, afterHeaderOffset);
            keyBuffer.flip();
            var keyBytes = new byte[keyLength];
            keyBuffer.get(keyBytes);
            var key = new String(keyBytes, StandardCharsets.UTF_8);

            var afterHeaderKeyOffset = afterHeaderOffset + keyLength;

            byte[] valueBytes;
            if (skipValue || valueLength == TOMBSTONE_VALUE_LENGTH) {
                valueBytes = new byte[0];
            } else {
                var valueBuffer = ByteBuffer.allocate(valueLength);
                fileChannel.read(valueBuffer, afterHeaderKeyOffset);
                valueBuffer.flip();
                valueBytes = new byte[valueLength];
                valueBuffer.get(valueBytes);
            }

            return new InternalEntry(key, valueBytes, offset, DATA_FILE_HEADER_SIZE + keyLength + valueLength);
        } catch (Exception e) {
            throw new RuntimeException("Problem while reading ADB data file entry", e);
        }
    }

    public static DataEntry readEntry(FileChannel fileChannel, long offset) {
        var entry = readEntry(fileChannel, offset, false);
        return new DataEntry(entry.key(), entry.value());
    }

    public static boolean canWriteNextEntry(long configuredDataFileSize, long currentDataFileSize, int entrySize) {
        return (configuredDataFileSize - currentDataFileSize) >= entrySize;
    }

    public static int entrySize(String key, byte[] value) {
        return DATA_FILE_HEADER_SIZE + key.length() + value.length;
    }

    public static WriteEntryResult writeNextEntry(FileOutputStream fileOS, String key, byte[] value) {
        try {
            var offset = fileOS.getChannel().position();

            var keyBytes = key.getBytes(StandardCharsets.UTF_8);

            byte keyLength = (byte) ((keyBytes.length) & NO_SIGN_BYTE_MASK);

            var valueLength = value.length;
            var valueLengthByte2 = (byte) ((valueLength >> 16) & NO_SIGN_BYTE_MASK);
            var valueLengthByte1 = (byte) ((valueLength >> 8) & NO_SIGN_BYTE_MASK);
            var valueLengthByte0 = (byte) ((valueLength) & NO_SIGN_BYTE_MASK);

            var entrySize = entrySize(key, value);
            var bytesOS = new ByteArrayOutputStream(entrySize);
            bytesOS.write(keyLength);
            bytesOS.write(valueLengthByte2);
            bytesOS.write(valueLengthByte1);
            bytesOS.write(valueLengthByte0);

            bytesOS.write(keyBytes);
            if (value.length > 0) {
                bytesOS.write(value);
            }

            fileOS.write(bytesOS.toByteArray());
            // TODO: maybe configurable guarantees, since sync call tends to be slooow
            fileOS.getFD().sync();

            return new WriteEntryResult(offset, entrySize);
        } catch (Exception e) {
            throw new RuntimeException("Fail to write next entry for the %s key".formatted(key), e);
        }
    }

    public record IndexEntry(String fileId, String key, long offset) {
    }

    public record DataEntry(String key, byte[] value) {
    }

    public record WriteEntryResult(long offset, int entrySize) {
    }

    private record InternalEntry(String key, byte[] value, long offset, int totalSize) {
    }
}
