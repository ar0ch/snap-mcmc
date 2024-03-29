package snap.app.analysis;

import jam.util.IconUtils;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import beast.app.BEASTVersion;
import beast.app.util.Arguments;
import beast.app.util.ErrorLogHandler;
import beast.app.util.MessageLogHandler;
import beast.app.util.Version;
import beast.util.Randomizer;

import snap.util.TreeSetAnalyser3;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.*;

public class TreeSetAnalyser {

    private final static Version version = new BEASTVersion();

    static class TSAConsoleApp extends jam.console.ConsoleApplication {

        public TSAConsoleApp(String nameString, String aboutString, javax.swing.Icon icon) throws IOException {
            super(nameString, aboutString, icon, false);
            getDefaultFrame().setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        }

        public void doStop() {
            // thread.stop is deprecated so need to send a message to running threads...
//            Iterator iter = parser.getThreads();
//            while (iter.hasNext()) {
//                Thread thread = (Thread) iter.next();
//                thread.stop(); // http://java.sun.com/j2se/1.5.0/docs/guide/misc/threadPrimitiveDeprecation.html
//            }
        }

        public void setTitle(String title) {
            getDefaultFrame().setTitle(title);
        }

        TreeSetAnalyser3 analyser;
    }

    public TreeSetAnalyser(TreeSetAnalyser3 analyser, TSAConsoleApp consoleApp, int maxErrorCount) {

        final Logger infoLogger = Logger.getLogger("beast.app");
        try {

            if (consoleApp != null) {
                consoleApp.analyser = analyser;
            }

            // Add a handler to handle warnings and errors. This is a ConsoleHandler
            // so the messages will go to StdOut..
            Logger logger = Logger.getLogger("beast");

            Handler handler = new MessageLogHandler();
            handler.setFilter(new Filter() {
                public boolean isLoggable(LogRecord record) {
                    return record.getLevel().intValue() < Level.WARNING.intValue();
                }
            });
            logger.addHandler(handler);

            logger.setUseParentHandlers(false);

            // This is a special logger that is for logging numerical and statistical errors
            // during the MCMC run. It will tolerate up to maxErrorCount before throwing a
            // RuntimeException to shut down the run.
            //Logger errorLogger = Logger.getLogger("error");
            handler = new ErrorLogHandler(maxErrorCount);
            handler.setLevel(Level.WARNING);
            logger.addHandler(handler);

            analyser.run();

        } catch (Exception ex) {

        	infoLogger.severe("Fatal exception: " + ex.getMessage());
            System.err.println("Fatal exception: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    static String getFileNameByDialog(String title) {
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        fc.addChoosableFileFilter(new FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String name = f.getName().toLowerCase();
                if (name.endsWith(".xml")) {
                    return true;
                }
                return false;
            }

            // The description of this filter
            public String getDescription() {
                return "xml files";
            }
        });

        fc.setDialogTitle(title);
        int rval = fc.showOpenDialog(null);

        if (rval == JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile().toString();
        }
        return null;
    } // getFileNameByDialog


    public static void centreLine(String line, int pageWidth) {
        int n = pageWidth - line.length();
        int n1 = n / 2;
        for (int i = 0; i < n1; i++) {
            System.out.print(" ");
        }
        System.out.println(line);
    }

    public static void printTitle() {
        System.out.println();
        centreLine("SNAPP " + version.getVersionString() + ", " + version.getDateString(), 60);
        centreLine("Tree Set Analyser", 60);
        for (String creditLine : version.getCredits()) {
            centreLine(creditLine, 60);
        }
        System.out.println();

    }

    public static void printUsage(Arguments arguments) {
        arguments.printUsage("treeSetAnalyser", "[<input-file-name>]");
        System.exit(0);
    }

    //Main method
    public static void main(String[] args) throws java.io.IOException {
    	List<String> TSAargs = new ArrayList<String>();

        Arguments arguments = new Arguments(
                new Arguments.Option[]{
                        new Arguments.Option("window", "Provide a console window"),
                        new Arguments.Option("options", "Display an options dialog"),
                        new Arguments.Option("working", "Change working directory to input file's directory"),
                        new Arguments.StringOption("tree", "newick tree", "check whether this tree is in the 95% credible set"),
                        new Arguments.IntegerOption("burnin", "Percentage of trees to be considered burn-in, default 10%"),
                        new Arguments.StringOption("file", "input-filename", "tree set file"),
                        new Arguments.Option("help", "Print this information and stop"),
                });

        try {
            arguments.parseArguments(args);
        } catch (Arguments.ArgumentException ae) {
            System.out.println();
            System.out.println(ae.getMessage());
            System.out.println();
            printUsage(arguments);
            System.exit(1);
        }

        if (arguments.hasOption("help")) {
            printUsage(arguments);
            System.exit(0);
        }

        final boolean window = arguments.hasOption("window");
        final boolean options = arguments.hasOption("options");
        final boolean working = arguments.hasOption("working");
        String fileNamePrefix = null;

        long seed = Randomizer.getSeed();

        int maxErrorCount = 0;
        if (arguments.hasOption("errors")) {
            maxErrorCount = arguments.getIntegerOption("errors");
            if (maxErrorCount < 0) {
                maxErrorCount = 0;
            }
        }

        TSAConsoleApp consoleApp = null;

        String nameString = "SNAPP " + version.getVersionString();

        if (window) {
            System.setProperty("com.apple.macos.useScreenMenuBar", "true");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.showGrowBox", "true");
            System.setProperty("beast.useWindow", "true");

            javax.swing.Icon icon = IconUtils.getIcon(TreeSetAnalyser.class, "snapp.png");

            String aboutString = "<html><div style=\"font-family:sans-serif;\"><center>" +
                    "<div style=\"font-size:12;\"><p>SNAPP Tree Set Analyser<br>" +
                    "Version " + version.getVersionString() + ", " + version.getDateString() + "</p>" +
                    version.getHTMLCredits() +
                    "</div></center></div></html>";

            consoleApp = new TSAConsoleApp(nameString, aboutString, icon);
        }

        printTitle();

        File inputFile = null;

        if (options) {
            String titleString = "<html><center><p>SNAPP Tree Set Analyser<br>" +
                    "Version " + version.getVersionString() + ", " + version.getDateString() + "</p></center></html>";
            javax.swing.Icon icon = IconUtils.getIcon(TreeSetAnalyser.class, "snapp.png");

            TSADialog dialog = new TSADialog(new JFrame(), titleString, icon);

            if (arguments.hasOption("burnin")) {
                int burnin = arguments.getIntegerOption("burnin");
                dialog.burninText.setValue(burnin);
            } else {
                dialog.burninText.setValue(10);
            }
            if (arguments.hasOption("tree")) {
                dialog.newickText.setText(arguments.getStringOption("tree"));
            }
            if (arguments.hasOption("file")) {
                dialog.inputFileNameText.setText(arguments.getStringOption("file"));
            }
            if (!dialog.showDialog(nameString, seed)) {
            	System.exit(0);
                return;
            }

            inputFile = dialog.getInputFile();

                TSAargs.add("-b");
                TSAargs.add(dialog.burninText.getText());
            if (!dialog.newickText.getText().matches("\\s*")) {
                TSAargs.add("-tree");
                TSAargs.add(dialog.newickText.getText());
            }
        } else {
            if (arguments.hasOption("burnin")) {
                TSAargs.add("-b");
                TSAargs.add(arguments.getIntegerOption("burnin") + "");
            }
            if (arguments.hasOption("tree")) {
                TSAargs.add("-tree");
                TSAargs.add(arguments.getStringOption("tree"));
            }
            if (arguments.hasOption("file")) {
            	inputFile = new File(arguments.getStringOption("file"));
            }
        }

        if (inputFile == null) {

//            String[] args2 = arguments.getLeftoverArguments();
//
//            if (args2.length > 1) {
//                System.err.println("Unknown option: " + args2[1]);
//                System.err.println();
                printUsage(arguments);
//                return;
//            }
//
//            String inputFileName = null;
//
//
//            if (args2.length > 0) {
//                inputFileName = args2[0];
//                inputFile = new File(inputFileName);
//            }
//
//            if (inputFileName == null) {
//                // No input file name was given so throw up a dialog box...
//                inputFile = new File(getFileNameByDialog("BEAST " + version.getVersionString() + " - Select XML input file"));
//            }
        }

        if (inputFile != null && inputFile.getParent() != null && working) {
            System.setProperty("user.dir", inputFile.getParent());
        }

        if (window) {
            if (inputFile == null) {
                consoleApp.setTitle("null");
            } else {
                consoleApp.setTitle(inputFile.getName());
            }
        }

        // Construct the beast object
        TreeSetAnalyser3 analyser = new TreeSetAnalyser3();

        try {
            // set all the settings...
        	TSAargs.add(inputFile.getAbsolutePath());
        	if (analyser.parseArgs(TSAargs.toArray(new String[0]))) {
        		new TreeSetAnalyser(analyser, consoleApp, maxErrorCount);
        	}
       } catch (RuntimeException rte) {
            if (window) {
                // This sleep for 2 seconds is to ensure that the final message
                // appears at the end of the console.
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println();
                System.out.println("TreeSetAnalyser has terminated with an error. Please select QUIT from the menu.");
            }
            // logger.severe will throw a RTE but we want to keep the console visible
        } catch (Exception e) {
        	e.printStackTrace();
        }

        if (!window) {
            System.exit(0);
        }
    }
}


