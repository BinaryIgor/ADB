package com.binaryigor.adb;

import java.util.Optional;

public interface ADB {

    void put(String key, byte[] value);

    Optional<byte[]> get(String key);
}
