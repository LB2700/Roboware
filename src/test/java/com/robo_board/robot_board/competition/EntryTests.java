package com.robo_board.robot_board.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntryTests {
    @TempDir
    Path directory;

    @Test
    void findsRobotFolderByName() {
        Path folder = Entry.findRobotFolder("sample");

        assertTrue(Files.isDirectory(folder));
        assertEquals("sample", folder.getFileName().toString());
    }

    @Test
    void rejectsUnknownRobotFolder() {
        assertThrows(IllegalArgumentException.class,
                () -> Entry.findRobotFolder("folder-that-does-not-exist"));
    }

    @Test
    void compilesEveryJavaSubmissionInFolder() throws Exception {
        Path robotFolder = Files.createDirectory(directory.resolve("submissions"));
        Files.writeString(robotFolder.resolve("FirstRobot.java"),
                "package submissions; import robocode.AdvancedRobot; "
                        + "public class FirstRobot extends AdvancedRobot { public void run() {} }\n");
        Files.writeString(robotFolder.resolve("SecondRobot.java"),
                "package submissions; public class SecondRobot {}\n");

        Entry.compileSubmissions(robotFolder);

        assertTrue(Files.isRegularFile(robotFolder.resolve("FirstRobot.class")));
        assertTrue(Files.isRegularFile(robotFolder.resolve("SecondRobot.class")));
        byte[] classFile = Files.readAllBytes(robotFolder.resolve("FirstRobot.class"));
        int majorVersion = (Byte.toUnsignedInt(classFile[6]) << 8) | Byte.toUnsignedInt(classFile[7]);
        assertEquals(52, majorVersion);
    }

    @Test
    void compilationErrorIdentifiesSourceFileAndLocation() throws Exception {
        Path robotFolder = Files.createDirectory(directory.resolve("broken_submissions"));
        Path brokenSource = robotFolder.resolve("BrokenRobot.java");
        Files.writeString(brokenSource, "package broken_submissions;\n"
                + "public class BrokenRobot {\n"
                + "    this is not valid Java;\n"
                + "}\n");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Entry.compileSubmissions(robotFolder));

        assertTrue(failure.getMessage().contains("BrokenRobot.java:3:"));
        assertTrue(failure.getMessage().contains("error:"));
    }

    @Test
    void readsRobotNamesAndAssignsIndicesInRowOrder() throws Exception {
        Path robotFolder = Files.createDirectory(directory.resolve("submissions"));
        Path csv = robotFolder.resolve("metadata.csv");
        Files.writeString(csv, "\"Full name\",\"Student ID\",\"Robot Name\"\n"
                + "\"Brennan, Aoife\",\"102384751\",\"IronSpark\"\n"
                + "\"Liam O'Sullivan\",\"102471936\",\"Tank\"\"Buster\"\n");

        List<Entry> entries = Entry.readCsv(csv);

        assertEquals(List.of("submissions.IronSpark", "submissions.Tank\"Buster"),
                entries.stream().map(Entry::getRobotName).toList());
        assertEquals(List.of("Brennan, Aoife", "Liam O'Sullivan"),
                entries.stream().map(Entry::getAuthorName).toList());
        assertEquals(List.of(0, 1), entries.stream().map(Entry::getIndex).toList());
    }

    @Test
    void rejectsCsvWithoutRobotNameColumn() throws Exception {
        Path robotFolder = Files.createDirectory(directory.resolve("submissions"));
        Path csv = robotFolder.resolve("metadata.csv");
        Files.writeString(csv, "Full name,Student ID\nAoife Brennan,102384751\n");

        assertThrows(IllegalArgumentException.class, () -> Entry.readCsv(csv));
    }
}
