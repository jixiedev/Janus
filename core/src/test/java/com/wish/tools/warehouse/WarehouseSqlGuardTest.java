package com.wish.tools.warehouse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarehouseSqlGuardTest {

    @Test
    void allowsSelectAndWith() {
        assertEquals("SELECT 1", WarehouseSqlGuard.validateReadOnly("SELECT 1"));
        assertEquals("WITH t AS (SELECT 1) SELECT * FROM t", WarehouseSqlGuard.validateReadOnly("WITH t AS (SELECT 1) SELECT * FROM t"));
    }

    @Test
    void rejectsWriteStatements() {
        assertThrows(IllegalArgumentException.class, () -> WarehouseSqlGuard.validateReadOnly("DELETE FROM t"));
        assertThrows(IllegalArgumentException.class, () -> WarehouseSqlGuard.validateReadOnly("INSERT INTO t VALUES (1)"));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(IllegalArgumentException.class, () -> WarehouseSqlGuard.validateReadOnly("SELECT 1; SELECT 2"));
    }
}
