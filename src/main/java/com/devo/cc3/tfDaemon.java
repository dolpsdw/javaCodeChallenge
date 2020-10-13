package com.devo.cc3;

import static java.lang.Math.log;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

public class tfDaemon {

    public static void main(String[] args) {
        // Options definition -d dir -n 5 -p 300 -t "password try again"
        Options options = new Options();

        Option oDir = new Option("d", "directory", true, "directory to scan");
        oDir.setRequired(true);
        options.addOption(oDir);

        Option oNum = new Option("n", "number", true, "number of top results to show");
        oNum.setRequired(true);
        options.addOption(oNum);

        Option oPeriod = new Option("p", "period", true, "number of ms for scan the directory and refresh results");
        oPeriod.setRequired(true);
        options.addOption(oPeriod);

        Option oTerms = new Option("t", "terms", true, "string with the terms that should be searched");
        oTerms.setRequired(true);
        options.addOption(oTerms);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        File directory = null;
        Short number = null;
        Integer period = null;
        HashSet<String> terms = new HashSet<>();
        try { // Validation - Sanitization of options
            cmd = parser.parse(options, args);

            String sDirectory = cmd.getOptionValue("directory");
            directory = new File(sDirectory);
            if (!directory.exists()) {
                throw new IllegalArgumentException("Directory does not exist");
            }

            String sNumber = cmd.getOptionValue("number");
            number = Short.parseShort(sNumber);
            if (number < 1) {
                throw new IllegalArgumentException("minimum result to show 1");
            }

            String sPeriod = cmd.getOptionValue("period");
            period = Integer.parseInt(sPeriod);
            if (period < 42) {
                throw new IllegalArgumentException("minimum disk refresh 42");
            }

            String sTerms = cmd.getOptionValue("terms");
            for (String s : sTerms.split(" ")) {
                terms.add(s.toLowerCase()); // Sanitize input terms by toLower and remove duplicates with HashSet
            }
            if (terms.size() == 0) {
                throw new IllegalArgumentException("Introduce at least 1 term");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            formatter.printHelp("cc3-tfDaemon", options);

            System.exit(1);
        }

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
                terms.forEach( // initialize terms not found on documents to 0
                    term -> {
                        if (!documentsWithTerm.containsKey(term)) {
                            documentsWithTerm.put(term, 0);
                        }
                    }
                );
                return documentsWithTerm;
            }
        );
        Observable<HashMap<String, Float>> TermToIDF_Index$ = TermToPresence_Index$.map(
            TPIndex -> {
                HashMap<String, Float> IDFIndex = new HashMap<>();
                HashMap<String, ProcessLogFileResult> TFIndex = FileToTF_Index$.getValue(); // This could also achievable withLatestFrom
                terms.forEach(
                    term -> { // terms that are not in any documents will have a IDFIndex 0
                        if (TPIndex.get(term) == 0) { // Never divide by 0
                            IDFIndex.put(term, 0.0f); // since IDF is a log e calc, floor should be 0
                        } else {
                            // TFIndex should contain a file entry if file exist even with no terms on it -> TFIndex.size will match number of Files
                            // if (TFIndex.size() == 1) { // IF only 1 document it should be "relevant" instead of having TF*log(1) = 0
                            //     IDFIndex.put(term, Float.MIN_VALUE);
                            // } else {
                            IDFIndex.put(term, (float) log((float) TFIndex.size() / TPIndex.get(term)));
                            // }
                        }
                    }
                );
                return IDFIndex;
            }
        );
        Observable<HashMap<String, Float>> FileToTF_IDF_Index$ = TermToIDF_Index$.map(
            IDFIndex -> {
                HashMap<String, Float> FileToTF_IDF = new HashMap<>();
                HashMap<String, ProcessLogFileResult> TFIndex = FileToTF_Index$.getValue(); // This could also achievable withLatestFrom
                // TFIndex should contain a file entry if file exist even with no terms on it
                TFIndex.forEach(
                    (file, processLogFileResult) -> {
                        float fileTF_IDF = 0.0f;
                        for (String term : terms) {
                            fileTF_IDF += processLogFileResult.TermsFrequency.get(term) * IDFIndex.get(term);
                        }
                        FileToTF_IDF.put(file, fileTF_IDF);
                    }
                );
                return FileToTF_IDF;
            }
        );
        // Activate the Observable chain, and start the View rePaint cycle
        Short finalNumber = number;
        FileToTF_IDF_Index$.subscribe(
            TF_IDF_Index -> {
                clearConsole();
                TF_IDF_Index
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .limit(finalNumber)
                    .forEachOrdered(result -> System.out.printf("\n%s -> %s", result.getKey(), result.getValue()));
            },
            ko -> System.out.printf("\nERROR %s", ko)
        );

        /* Setup Folder observation
         *
         * Discussion: Java.WatchService vs apache.commons.io.monitor
         * WatchService:
         * ✓ Will fit nice since its event Based and no poll its needed
         * ✕ The Operative system can overflow the event queue https://stackoverflow.com/questions/39076626/how-to-handle-the-java-watchservice-overflow-event
         * ✕ Does not work on Shared network drives (docker?)
         *
         * commons.io.monitor:
         * ✕ Will poll the FileSystem folder each N ms and compare the tree and metadata? to fire events
         * ✓ The polling is done in another thread
         * ✓ Better Throughput
         * ✓ Work on all kind of drives, including network and docker
         * */
        FileAlterationObserver folderObserver = new FileAlterationObserver(directory);
        FileAlterationMonitor monitor = new FileAlterationMonitor(period);
        FileAlterationListener listener = new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                calculateTFAndFireObs(file, terms, FileToTF_Index$);
            }

            @Override
            public void onFileChange(File file) {
                // calculateTFAndFireObs(file, terms, FileToTF_Index$); // The implementation is supporting this scenario as well.
            }

            @Override
            public void onFileDelete(File file) { // The implementation is supporting this scenario as well.
                // try {
                //     HashMap<String, ProcessLogFileResult> TFIndex = FileToTF_Index$.getValue();
                //     TFIndex.remove(file.getCanonicalPath()); // No need to test null state
                //     FileToTF_Index$.onNext(TFIndex); // Send Event notification for reactions to happen.
                // } catch (IOException e) {
                //     e.printStackTrace();
                // }
            }
        };
        folderObserver.addListener(listener);
        monitor.addObserver(folderObserver);
        try {
            monitor.start(); // This will run a Thread that will poll the File System.
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        // Get all files under folder
        Collection<File> files = FileUtils.listFiles(directory, null, true);
        files.forEach(file -> calculateTFAndFireObs(file, terms, FileToTF_Index$)); // And process each
    }

    /**
     * Not Pure,  it modify global state.
     * Predictable, given the Same Terms, the same FileToTF_Index$ and the same file with the same contents the same onNext will happen
     * The @Output is activate the Observable chain with an updated TF_Index
     * @param file file to calc
     * @param terms terms to base the calculations
     * @param fileToTF_Index$ BehaviorSubject<HashMap<String, ProcessLogFileResult>> to update
     */
    private static void calculateTFAndFireObs(
        File file,
        HashSet<String> terms,
        BehaviorSubject<HashMap<String, ProcessLogFileResult>> fileToTF_Index$
    ) {
        try {
            ProcessLogFileResult result = processLogFile(file, terms); // When the file read operation end
            HashMap<String, ProcessLogFileResult> TFIndex = fileToTF_Index$.getValue();
            TFIndex.put(file.getCanonicalPath(), result); // No need to test null state
            fileToTF_Index$.onNext(TFIndex); // Send Event notification for reactions to happen.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Almost @Pure, given the same File and Terms will output the Same. But read a File is considered IO side Effect
     * This function will sequentially read a file, and calculate the TermFreq for a given input terms.
     * Will also calculate TermPresence for optimized TermPresence and IDF calc.
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
        // TODO: Battle Test this, and check if only one file at a time is being read.
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

        /**
         * Inner Static class for complex return type.
         * @param termsFrequency the final HashMap<Term, Float> of each term.
         * @param termsPresence the final HashSet for easy aggregation of files with a term
         */
        public ProcessLogFileResult(final HashMap<String, Float> termsFrequency, final HashSet<String> termsPresence) {
            TermsFrequency = termsFrequency;
            TermsPresence = termsPresence;
        }
    }

    /**
     * Clear the console on Windows cmd or Linux like.
     */
    public static void clearConsole() {
        try {
            final String os = System.getProperty("os.name");
            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (IOException | InterruptedException ex) {
            System.out.println("----------------------------------------------------------------------------");
        }
    }
}
