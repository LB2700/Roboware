package com.robo_board.robot_board.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryTests {
    @TempDir
    Path directory;

    @Test
    void readsRobotNamesAndAssignsIndicesInRowOrder() throws Exception {
        Path csv = directory.resolve("metadata.csv");
        Files.writeString(csv, "\"Full name\",\"Student ID\",\"Robot Name\"\n"
                + "\"Brennan, Aoife\",\"102384751\",\"IronSpark\"\n"
                + "\"Liam O'Sullivan\",\"102471936\",\"Tank\"\"Buster\"\n");

        List<Entry> entries = Entry.readCsv(csv);

        assertEquals(List.of("IronSpark", "Tank\"Buster"),
                entries.stream().map(Entry::getRobotName).toList());
        assertEquals(List.of(0, 1), entries.stream().map(Entry::getIndex).toList());
    }

    @Test
    void rejectsCsvWithoutRobotNameColumn() throws Exception {
        Path csv = directory.resolve("metadata.csv");
        Files.writeString(csv, "Full name,Student ID\nAoife Brennan,102384751\n");

        assertThrows(IllegalArgumentException.class, () -> Entry.readCsv(csv));
    }
}
