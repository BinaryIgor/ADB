package com.binaryigor.adb;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ADBFiles {

    private static final String DATA_FILE_NAME = "data";

    public static List<Entry> scanAllIndexEntries(Path rootDir) {
        if (!Files.isDirectory(rootDir)) {
            return List.of();
        }

        try (var files = Files.list(rootDir)) {
            return files.filter(f -> f.toString().contains(DATA_FILE_NAME))
                    .flatMap(ADBFiles::indexEntries).toList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Entry> indexEntries(Path dataFilePath) {
        var entries = new ArrayList<Entry>();

        Entry previousEntry = null;

        var file = dataFilePath.toFile();

        try (var is = new FileInputStream(file)) {
            while (is.available() > 0) {
                var entry = readNextEntry(is, previousEntry);
                entries.add(entry);
                previousEntry = entry;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entries.stream();
    }

    // byte buffer optimization based on metadata...
    public static Entry readAtOffsetEntry(FileChannel channel, int offset) {
//        var buffer = ByteBuffer.allocate(10_000);
//        channel.read(buffer, 0);
        return null;
    }

    public static Entry readNextEntry(FileInputStream inputStream, Entry previousEntry) {
        // TODO: checks
        try {
            // TODO: configurable?
            var keyLength = inputStream.read() & 0xFF;
            var keyBytes = inputStream.readNBytes(keyLength);

            var key = new String(keyBytes, StandardCharsets.UTF_8);

            var valueLengthByte3 = (byte) inputStream.read();
            var valueLengthByte2 = (byte) inputStream.read();
            var valueLengthByte1 = (byte) inputStream.read();
            var valueLengthByte0 = (byte) inputStream.read();


            var valueLength = ((valueLengthByte3 & 0xFF) << 24) | ((valueLengthByte2 & 0xFF) << 16) |
                    ((valueLengthByte1 & 0xFF) << 8) | (valueLengthByte0 & 0xFF);

            // TOOD: could have read value...
//            inputStream.skip(valueLength);

            // TODO: debugs, remove!
            var value = inputStream.readNBytes(valueLength);
            System.out.println("Key length: " + keyLength);
            System.out.println("Key: " + key);
            System.out.println("Value length: " + valueLength);
            System.out.println("Value: " + new String(value, StandardCharsets.UTF_8));
            System.out.println();

            var offset = Optional.ofNullable(previousEntry)
                    .map(e -> e.offsetEnd)
                    .orElse(0);
            var offsetEnd = offset + 1 + keyLength + 4 + valueLength;

            return new Entry(offset, offsetEnd, key, new byte[0]);
        } catch (Exception e) {
            throw new RuntimeException("Problem while reading ADBFile entry", e);
        }
    }

    public static void writeNextEntry(FileOutputStream fileOS, String key, byte[] value) {
        try {
            // write only if...
            var keyBytes = key.getBytes(StandardCharsets.UTF_8);

            var bytesOS = new ByteArrayOutputStream();
            byte keyLength = (byte) ((keyBytes.length) & 0xFF);
            bytesOS.write(keyLength);
            bytesOS.write(keyBytes);

            // TODO: check acceptable value length!
            var valueLength = value.length;
            byte valueLengthByte3 = (byte) ((valueLength >> 24) & 0xFF);
            byte valueLengthByte2 = (byte) ((valueLength >> 16) & 0xFF);
            byte valueLengthByte1 = (byte) ((valueLength >> 8) & 0xFF);
            byte valueLengthByte0 = (byte) ((valueLength) & 0xFF);

            bytesOS.write(valueLengthByte3);
            bytesOS.write(valueLengthByte2);
            bytesOS.write(valueLengthByte1);
            bytesOS.write(valueLengthByte0);
            bytesOS.write(value);

            // if there is a space, if not, create new data file
            fileOS.write(bytesOS.toByteArray());
            fileOS.getFD().sync();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public record Entry(int offsetStart, int offsetEnd, String key, byte[] value) {
    }
}
