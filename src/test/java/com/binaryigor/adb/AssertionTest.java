package com.binaryigor.adb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AssertionTest {

    @Test
    void runsIt() {
        Assertions.assertEquals(4, 2 + 2);
    }
}
