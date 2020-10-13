package com.devo.cc3;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class tfDaemonTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private String folderSample = "";

    @Before
    public void setUp() throws Exception {
        // Arrange
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        URL folderSampleURL = classloader.getResource("testTFSample");
        assert folderSampleURL != null;
        folderSample = folderSampleURL.getFile();
    }

    @After
    public void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * This is a global e2e Test of the whole Daemon
     */
    @Test
    public void main() {
        // Arrange
        // Act
        com.devo.cc3.tfDaemon.main(new String[] { "-d", folderSample, "-n", "2", "-p", "333", "-t", "Error Warning" });

        // Assert
        String[] consoleOutput = outContent.toString().split("\n");
        int lines = consoleOutput.length;
        assertTrue("First position and correct Calc for debug.log", consoleOutput[lines - 2].contains("debug.log -> 1.1008807"));
        assertTrue("Second position and correct Calc for debug2.log", consoleOutput[lines - 1].contains("debug2.log -> 0.2157478"));
    }

    /**
     * This Test is more in the Unit Test Fashion
     * Increase coverage could be easy extracting the Observable logic to function classes, but that will increase the memory footprint.
     */
    @Test
    public void processLogFile() {
        // Arrange
        File file = new File(folderSample.concat("/debug.log"));
        HashSet<String> terms = new HashSet<>(Arrays.asList("error", "warning", "Asklepios"));
        tfDaemon.ProcessLogFileResult result = null;
        // Act
        try {
            result = tfDaemon.processLogFile(file, terms);
        } catch (IOException e) {
            fail("Exception thrown");
        }

        // Assert
        assertEquals("TF Calc is correct for Error", 4.220588207244873, result.TermsFrequency.get("error"), 0.0);
        assertEquals("TF Calc is correct for Asklepios", 0.0, result.TermsFrequency.get("Asklepios"), 0.0);
        assertTrue("Presence is correct for Error", result.TermsPresence.contains("error"));
        assertFalse("Presence is correct for Asklepios", result.TermsPresence.contains("Asklepios"));
    }
}
