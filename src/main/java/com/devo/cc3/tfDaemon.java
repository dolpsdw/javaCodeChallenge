package com.devo.cc3;

import io.reactivex.rxjava3.core.Flowable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

public class tfDaemon {

    public static void main(String[] args) {
        Flowable.just("Hello world").subscribe(System.out::println);
        File testFile = new File("C:\\Users\\Jesus\\debug.log");
        HashSet<String> testInput = new HashSet<>();
        testInput.add("Error");
        testInput.add("Warning");
        try {
            ProcessLogFileResult result = processLogFile(testFile, testInput);
            System.out.println(result.TermFrequency);
            result.TermPresence.forEach(s -> System.out.println(s));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lest start from the minimum event function the one that read a file and calculate the tf.

    /**
     * This function will sequentially read a file, and calculate the TermFreq for a given input terms.
     * The read will be line by line to keep the Space Efficiency O(Constant) despite the file size.
     * Its better not run this function in multiple Threads since HDD performs better when the next read request is to
     * a near sector.
     * And in Parallel scenarios HDD will jump to far sectors while requesting a line from other file.
     * @param file the file to read
     * @param terms the terms to calculate TermFreq, and TermPresence
     * @return Object an Anonymous Object with the TF and TP
     * @throws IOException exceptions can happen while reading the file
     */
    public static ProcessLogFileResult processLogFile(File file, HashSet<String> terms) throws IOException {
        HashSet<String> homogenizedTerms = new HashSet<>(); // Case homogenization
        terms.forEach(s -> homogenizedTerms.add(s.toLowerCase()));
        HashSet<String> totalTermsInDocument = new HashSet<>();
        HashMap<String, Integer> termAggregation = new HashMap<>();
        // Regex Optimization
        Pattern pattern = Pattern.compile("[a-zA-Z]+");
        Matcher matcher = pattern.matcher("");
        try (LineIterator it = FileUtils.lineIterator(file)) {
            while (it.hasNext()) {
                String line = it.nextLine();
                matcher.reset(line);
                while (matcher.find()) {
                    String word = matcher.group().toLowerCase(); // Case homogenization
                    totalTermsInDocument.add(word);
                    if (homogenizedTerms.contains(word)) {
                        Integer wordAcc = termAggregation.get(word);
                        termAggregation.put(word, wordAcc == null ? 1 : wordAcc + 1);
                    }
                }
            }
        }
        float totalTermCount = 0.0f; // It is important to use a float to keep precision in the termFrequency division.
        for (int termCount : termAggregation.values()) {
            totalTermCount += termCount;
        }
        float termFrequency = 0.0f;
        if (totalTermsInDocument.size() != 0) { // Never divide by 0
            termFrequency = totalTermCount / totalTermsInDocument.size();
        }
        float finalTermFrequency = termFrequency;
        HashSet<String> finalTermPresence = new HashSet<>(termAggregation.keySet());
        return new ProcessLogFileResult(finalTermFrequency, finalTermPresence);
    }

    /**
     * Inner Static class for complex return type.
     */
    public static class ProcessLogFileResult {
        final Float TermFrequency;
        final HashSet<String> TermPresence;

        public ProcessLogFileResult(final Float termFrequency, final HashSet<String> termPresence) {
            TermFrequency = termFrequency;
            TermPresence = termPresence;
        }
    }
}
