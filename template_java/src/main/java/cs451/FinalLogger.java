package cs451;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;

public class FinalLogger {

     public static void writeLogs(String outputFilePath, Queue<String> logs) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath, true))) {
            while (!logs.isEmpty()) {
                String log = logs.poll();
                writer.write(log);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
