package com.devo.cc3;

import static java.lang.Math.log;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
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
        // Sanitize input terms by toLower and remove duplicates with HashSet
        HashSet<String> testInput = new HashSet<>();
        testInput.add("error");
        testInput.add("warning");
        // Lets put some reactive state in
        BehaviorSubject<HashMap<String, ProcessLogFileResult>> FileToTF_Index$ = BehaviorSubject.createDefault(new HashMap<>());
        Observable<HashMap<String, Integer>> TermToPresence_Index$ = FileToTF_Index$.map(
            TFIndex -> {
                HashMap<String, Integer> documentsWithTerm = new HashMap<>(); // This open the door to modify a file and update TFIndex
                TFIndex.forEach( // for each file in TFIndex cheap recalculate TPIndex
                    (file, processLogFileResult) -> { // TFIndex should contain a file entry if file exist even with no terms on it
                        processLogFileResult.TermsPresence.forEach( // access relevant terms per file
                            term -> {
                                Integer nDocuments = documentsWithTerm.get(term); // get the current value for a given term
                                documentsWithTerm.put(term, nDocuments == null ? 1 : nDocuments + 1); // index the number of files that contains it
                            }
                        );
                    }
                );
                testInput.forEach( // initialize terms not found on documents to 0
                    term -> {
                        if (!documentsWithTerm.containsKey(term)) {
                            documentsWithTerm.put(term, 0);
                        }
                    }
                );
                return documentsWithTerm;
            }
        );
        Observable<HashMap<String, Float>> FileToIDF_Index$ = TermToPresence_Index$.map(
            TPIndex -> {
                HashMap<String, Float> IDFIndex = new HashMap<>();
                HashMap<String, ProcessLogFileResult> TFIndex = FileToTF_Index$.getValue(); // This could also achievable withLatestFrom
                testInput.forEach(
                    term -> { // terms that are not in any documents will have a IDFIndex 0
                        if (TPIndex.get(term) == 0) { // Never divide by 0
                            IDFIndex.put(term, 0.0f); // since IDF is a log e calc, floor should be 0
                        } else {
                            // TFIndex should contain a file entry if file exist even with no terms on it -> TFIndex.size will match number of Files
                            if (TFIndex.size() == 1) { // IF only 1 document it should be "relevant" instead of having TF*log(1) = 0
                                IDFIndex.put(term, Float.MIN_VALUE);
                            } else {
                                IDFIndex.put(term, (float) log((float) TFIndex.size() / TPIndex.get(term)));
                            }
                        }
                    }
                );
                return IDFIndex;
            }
        );
        Observable<HashMap<String, Float>> FileToTF_IDF_Index$ = FileToIDF_Index$.map(
            IDFIndex -> {
                HashMap<String, Float> FileToTF_IDF = new HashMap<>();
                HashMap<String, ProcessLogFileResult> TFIndex = FileToTF_Index$.getValue(); // This could also achievable withLatestFrom
                // TFIndex should contain a file entry if file exist even with no terms on it
                TFIndex.forEach(
                    (file, processLogFileResult) -> {
                        processLogFileResult.TermsFrequency.forEach(
                            (term, tf) -> {
                                FileToTF_IDF.put(file, tf * IDFIndex.get(term));
                            }
                        );
                    }
                );
                return FileToTF_IDF;
            }
        );
        FileToTF_IDF_Index$.subscribe(
            TF_IDF_Index -> {
                TF_IDF_Index.forEach((file, result) -> System.out.printf("%s -> TF:%s", file, result));
            },
            ko -> System.out.printf("\nERROR %s", ko)
        );
        File testFile = new File("C:\\Users\\Jesus\\debug.log");
        try {
            ProcessLogFileResult result = processLogFile(testFile, testInput); // When the file read operation end
            HashMap<String, ProcessLogFileResult> TFIndex = FileToTF_Index$.getValue();
            TFIndex.put(testFile.getCanonicalPath(), result); // No need to test null state
            FileToTF_Index$.onNext(TFIndex); // Send Event notification for reactions to happen.
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
     * ---------Edge cases to Test---------
     * File not exist (removed when the function start) -> This will not happen per enunciate
     * File is empty
     * Terms are empty
     * @param file the file to read
     * @param terms the homogenized terms to calculate TermFreq, and TermPresence
     * @return ProcessLogFileResult static inner class with the TFDictionary and TP
     * @throws IOException exceptions can happen while reading the file
     */
    public static ProcessLogFileResult processLogFile(File file, HashSet<String> terms) throws IOException {
        HashSet<String> totalTermsInDocument = new HashSet<>();
        HashMap<String, Float> termsFrequency = new HashMap<>();
        HashSet<String> termsPresence = new HashSet<>();
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
                    totalTermsInDocument.add(word); // Dictionary will remove duplicates
                    if (terms.contains(word)) {
                        Integer wordAcc = termAggregation.get(word);
                        termAggregation.put(word, wordAcc == null ? 1 : wordAcc + 1);
                    }
                }
            }
        }
        terms.forEach( // initialize terms not in file to TF = 0 and populate termsPresence
            term -> {
                if (!termAggregation.containsKey(term)) {
                    termAggregation.put(term, 0);
                } else {
                    termsPresence.add(term);
                }
            }
        );
        // Calculate TF of each term
        termAggregation.forEach(
            (term, occurrences) -> {
                if (totalTermsInDocument.size() == 0) { // if the document has no words (binary?)
                    termsFrequency.put(term, 0.0f);
                } else {
                    termsFrequency.put(term, (float) occurrences / totalTermsInDocument.size()); // will not be divided by 0
                }
            }
        );
        return new ProcessLogFileResult(termsFrequency, termsPresence);
    }

    /**
     * Inner Static class for complex return type.
     */
    public static class ProcessLogFileResult {
        final HashMap<String, Float> TermsFrequency;
        final HashSet<String> TermsPresence;

        public ProcessLogFileResult(final HashMap<String, Float> termsFrequency, final HashSet<String> termsPresence) {
            TermsFrequency = termsFrequency;
            TermsPresence = termsPresence;
        }
    }
}
