package cs451;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainTest {

    @TempDir
    File tempDir;

    @Test
    public void testRetrieve() throws IOException {
        // Create a temporary configuration file
        File configFile = new File(tempDir, "config.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("10 20\n");
        }

        // Test retrieve method for position 0
        int result0 = Main.retrieve(configFile.getAbsolutePath(), 0);
        assertEquals(10, result0, "Expected value at position 0 is 10");

        // Test retrieve method for position 1
        int result1 = Main.retrieve(configFile.getAbsolutePath(), 1);
        assertEquals(20, result1, "Expected value at position 1 is 20");

        // Test retrieve method for an invalid position
        int resultInvalid = Main.retrieve(configFile.getAbsolutePath(), 2);
        assertEquals(-1, resultInvalid, "Expected value at invalid position is -1");
    }
}