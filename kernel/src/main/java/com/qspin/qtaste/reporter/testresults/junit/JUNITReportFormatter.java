package com.qspin.qtaste.reporter.testresults.junit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.qspin.qtaste.config.StaticConfiguration;
import com.qspin.qtaste.config.TestEngineConfiguration;
import com.qspin.qtaste.kernel.engine.TestEngine;
import com.qspin.qtaste.reporter.ReportFormatter;
import com.qspin.qtaste.reporter.testresults.TestResult;
import com.qspin.qtaste.reporter.testresults.TestResult.Status;
import com.qspin.qtaste.reporter.testresults.TestResultsReportManager;
import com.qspin.qtaste.util.FileUtilities;
import com.qspin.qtaste.util.Log4jLoggerFactory;
import com.qspin.qtaste.util.NamesValuesList;

public class JUNITReportFormatter extends ReportFormatter {
    private static Logger logger = Log4jLoggerFactory.getLogger(JUNITReportFormatter.class);
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static final String RESTART_SUT = "Restart SUT";
    private static final String START_SUT = "Start SUT";
    private static final String STOP_SUT = "Stop SUT";
    private static String templateStart;
    private static String templateRefresh;
    private static String templateEnd;
    private static String outputDir;
    private static TestEngineConfiguration config = TestEngineConfiguration.getInstance();

    private String templateStartContent;
    private String templateRefreshContent;
    private String templateEndContent;

    private String testSuite;
    private boolean reportStopStartSUT;
    private boolean reportReStartSUT;
    private boolean reportFirstFailure;

    private int numberOfAlreadyPrintedResult = 0;

    static {
        String template_root = config.getString("reporting.junit_template");
        if (!new File(template_root).isAbsolute()) {
            template_root = StaticConfiguration.QTASTE_ROOT + File.separator + template_root;
        }
        templateStart = template_root + File.separator + "report_template_start.xml";
        templateRefresh = template_root + File.separator + "report_template_refresh.xml";
        templateEnd = template_root + File.separator + "report_template_end.xml";
        outputDir = config.getString("reporting.generated_report_path");
    }

    // Need to have that signature for instantiation
    public JUNITReportFormatter(String notUsed) throws IOException {
        super(new File(outputDir), "WillBeErased");
        templateStartContent = FileUtilities.readFileContent(templateStart);
        templateRefreshContent = FileUtilities.readFileContent(templateRefresh);
        templateEndContent = FileUtilities.readFileContent(templateEnd);
        reportStopStartSUT = config.getBoolean("reporting.junit_settings.report_stop_start_sut");
        reportReStartSUT = config.getBoolean("reporting.junit_settings.report_restart_sut");
        reportFirstFailure = config.getBoolean("reporting.junit_settings.report_first_failure");
    }

    @Override
    public void startReport(Date timeStamp, String name) {
        // Intentionally no call to super
        startDate = timeStamp;
        testSuite = TestEngine.getCurrentTestSuite().getName();

        String testSuiteName = testSuite;

        NamesValuesList<String, String> namesValues = new NamesValuesList<>();

        // TestSuite related attributes
        namesValues.add("###TS_NAME###", testSuiteName);
        namesValues.add("###TS_TIMESTAMP###", DATE_FORMAT.format(startDate));
        namesValues.add("###TS_HOSTNAME###", "QTaste");

        logger.info("testSuite: [" + testSuite + "]");
        logger.info("reportDirectory: [" + reportDirectory + "]");

        String reportFileName = (testSuite.replace(File.separatorChar, '_') + "-qtaste.xml").replace(' ', '_').trim();
        reportFile = new File(reportDirectory,
              new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(startDate) + File.separator + reportFileName);
        if (!reportFile.getParentFile().exists()) {
            reportFile.getParentFile().mkdirs();
        }

        output = null;
        try {
            output = new PrintWriter(new BufferedWriter(new FileWriter(reportFile)));
            substituteAndWriteFile(templateStartContent, namesValues);
        } catch (Exception e) {
            logger.error("Cannot create the JUnit report", e);
        } finally {
            IOUtils.closeQuietly(output);
            //logger.debug("File created");
        }

    }

    public void writeTestResult(TestResult result) throws IOException {
        //logger.debug("Writing result");

        NamesValuesList<String, String> namesValues = new NamesValuesList<>();

        // One file per test.
        boolean fail = result.getStatus() == Status.FAIL;
        boolean error = result.getStatus() != Status.FAIL && result.getStatus() != Status.SUCCESS;
        //logger.debug(fail ? "Test failure" : error ? "Test in error" : "Test succeed");
        boolean isFirstAttempt = result.getRetryCount() == 0;
        String testCaseName = result.getId();
        if (!reportFirstFailure && fail && isFirstAttempt && !START_SUT.equalsIgnoreCase(testCaseName) && !STOP_SUT
              .equalsIgnoreCase(testCaseName) && !RESTART_SUT.equalsIgnoreCase(testCaseName)) {
            // Don't log the first failure (except for Start/Stop/Restart SUT).
            //logger.debug("First failure : don't create report");
            return;
        }
        if (!reportStopStartSUT && (START_SUT.equalsIgnoreCase(testCaseName) || STOP_SUT.equalsIgnoreCase(testCaseName))) {
            // Don't log Start/Stop SUT.
            //logger.debug("Start/Stop SUT : don't create report");
            return;
        }
        if (!reportReStartSUT && RESTART_SUT.equalsIgnoreCase(testCaseName)) {
            // Don't log Restart SUT.
            //logger.debug("Restart SUT : don't create report");
            return;
        }
        String elapsedTimeInS = String.format("%.4f", result.getElapsedTimeMs() / 1000.0);
        String testBedName = getTestbedConfigurationName();
        String testSuiteName = testSuite;
        reportFileName = (testSuiteName + "-qtaste.xml").replace(' ', '_').trim();

        // TestCase related attributes
        namesValues.add("###TC_CLASSNAME###", testBedName);
        namesValues.add("###TC_NAME###", testCaseName);
        namesValues.add("###TC_TIME###", elapsedTimeInS);

        // Result of the test
        String failedReason = result.getExtraResultDetails();
        if (result.getStackTrace() != null && result.getStackTrace().length() > 0) {
            failedReason += "\nStack trace:" + result.getStackTrace();
        }
        String resultXML = "/>";
        if (fail || error) {
            resultXML = "><" + (fail ? "failure" : "error") + " type=\"" + StringEscapeUtils.escapeXml(
                  result.getFailedFunctionId()) + "\">" + StringEscapeUtils.escapeXml(failedReason) + "</" + (fail ? "failure" :
                  "error") + "></testcase>";
        }

        namesValues.add("###RESULT###", resultXML);

        output = null;
        try {
            output = new PrintWriter(new BufferedWriter(new FileWriter(reportFile, true)));
            substituteAndWriteFile(templateRefreshContent, namesValues);
        } finally {
            IOUtils.closeQuietly(output);
            //logger.debug("File updated");
        }
    }

    @Override
    public void stopReport() {
        endDate = new Date();

        NamesValuesList<String, String> namesValues = new NamesValuesList<>();

        output = null;
        try {
            output = new PrintWriter(new BufferedWriter(new FileWriter(reportFile, true)));
            substituteAndWriteFile(templateEndContent, namesValues);
        } catch (Exception e) {
            logger.error("Cannot close the JUnit report", e);
        } finally {
            IOUtils.closeQuietly(output);
            //logger.debug("File closed");
        }
    }

    @Override
    public void refresh() {
        try {
            ArrayList<TestResult> results = TestResultsReportManager.getInstance().getResults();
            int numberOfResults = results.size();
            for (int i = numberOfAlreadyPrintedResult; i < numberOfResults; i++) {
                TestResult result = results.get(i);
                if (result.getStatus() != Status.RUNNING) {
                    writeTestResult(result);
                    numberOfAlreadyPrintedResult++;
                }
            }
        } catch (IOException e) {
            logger.error("Cannot refresh the JUnit report", e);
        }
    }
}
