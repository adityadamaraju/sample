package org.cohbe.test;

import java.io.*;
import java.util.*;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.support.http.HttpClientSupport;
import com.eviware.soapui.model.project.ProjectFactoryRegistry;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.model.testsuite.AssertionError;
import junit.framework.TestCase;
import org.apache.log4j.*;
import org.junit.Test;

import com.eviware.soapui.tools.SoapUITestCaseRunner;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(value = Parameterized.class)
public class CohbeUnitTestCase
{
  public static final String testcaseDirLoc = "./TestSuites/Smoke_Test/";

  public static final String soauiSuffix = "-soapui-project";

  private com.eviware.soapui.model.testsuite.TestCase soapuiTestCase;

  private boolean logSoauiMessages = false;

  protected static final Logger log = Logger.getLogger(CohbeUnitTestCase.class);

  public CohbeUnitTestCase(com.eviware.soapui.model.testsuite.TestCase soapuiTestCase)
  {
    this.soapuiTestCase = soapuiTestCase;
    if(!logSoauiMessages)
      disableSoapUILoggers();
    log.setLevel(Level.INFO);
    Appender appender = new ConsoleAppender();
    PatternLayout layout = new PatternLayout();
    layout.setConversionPattern("%-4r [%t] %-5p %c %x - %m%n");
    appender.setLayout(layout);
    log.addAppender(appender);
  }

  @Parameterized.Parameters
  public static Collection<Object[]> getSoapUITestCases()
  {
    Collection<Object[]> testCases = new ArrayList<Object[]>();
    for(String fileName : new File(testcaseDirLoc).list())
    {
      int index = fileName.lastIndexOf(".");
      if(index != -1 && fileName.substring(index, fileName.length()).equalsIgnoreCase(".xml"))
      {
        testCases.addAll(getTestCases(testcaseDirLoc + fileName, null));
      }
    }
    return testCases;
  }

  @Test
  public void masterTestCase() throws Exception
  {
    SoapUITestCaseRunner runner = new SoapUITestCaseRunner();
    String projectFileName = soapuiTestCase.getTestSuite().getProject().getName();
    String testSuiteName = soapuiTestCase.getTestSuite().getName();
    String testCaseName = soapuiTestCase.getName();
    long startTime = System.currentTimeMillis();
    runner.setProjectFile(testcaseDirLoc + projectFileName + soauiSuffix + ".xml");
    runner.setProjectProperties(getProperties(projectFileName, testSuiteName, testCaseName));
    runner.setTestCase(soapuiTestCase.getName());
    runner.setPrintReport(logSoauiMessages);
    runner.setJUnitReport(true);
    runner.setOutputFolder("./Reports");
    log.info("################################################################");
    log.info(
      new StringBuffer("Running Test Case : [").append(testCaseName).append("]  (Test Suite : [").append(testSuiteName)
        .append("] Project : [").append(projectFileName).append("])").toString());
    runner.run();
    long endTime = System.currentTimeMillis();
    List<com.eviware.soapui.model.testsuite.TestCase> failedTests = runner.getFailedTests();
    if(failedTests.size() > 0)
    {
      StringBuffer errorSB = new StringBuffer();
      errorSB.append("\nTest Case Failed - Test Case Name :" + testCaseName)
        .append("\nTest Suite : ").append(testSuiteName)
        .append("\nTotal request Assertions : ").append(runner.getAssertions().size());
      StringBuffer failedAssertionsSB = new StringBuffer();
      int failedAssertionsCount = 0;
      for(TestAssertion assertion : runner.getAssertions())
      {
        failedAssertionsSB.append("Assertion [").append(assertion.getName()).append("] has status ")
          .append(assertion.getStatus());
        if(assertion.getStatus().equals(Assertable.AssertionStatus.FAILED))
        {
          for(AssertionError error : assertion.getErrors())
          {
            failedAssertionsSB.append("\nASSERTION FAILED -> " + error.getMessage());
            failedAssertionsCount++;
          }
        }
      }

      errorSB.append("(Failed : ").append(failedAssertionsCount)
        .append("\nAssertions Details: ").append(failedAssertionsSB.toString());

      TestCase.assertTrue(errorSB.toString(), false);
    }
    log.info("Finished running Test Case [" + testCaseName + "], time taken: "
      + (endTime - startTime) + "ms, STATUS: " +
      (failedTests.size() > 0 ? TestRunner.Status.FAILED : TestRunner.Status.FINISHED));
  }

  private String[] getProperties(String projectName, String testSuiteName, String testCaseName) throws IOException
  {
    Properties properties = new Properties();
    //load the test case params from the global test case params properties file (TestParams.properties)
    loadProperties(properties, testcaseDirLoc + "TestParams.properties", true);
    // load the properties specific to this test case only (<project file name>.properties)
    //properties provided in this property file override the ones in TestParams.properties
    loadProperties(properties, testcaseDirLoc + projectName + ".properties", false);
    loadProperties(properties, testcaseDirLoc + testSuiteName + ".properties", false);
    loadProperties(properties, testcaseDirLoc + testCaseName + ".properties", false);
    String[] params = new String[properties.size()];
    int index = 0;
    for(Map.Entry<Object, Object> paramEntry : properties.entrySet())
    {
      params[index++] = paramEntry.getKey() + "=" + paramEntry.getValue();
    }

    return params;
  }

  private void loadProperties(Properties properties, String fileName, boolean required) throws IOException
  {
    //load the test case params from the global test case params properties file (TestParams.properties)
    File file = new File(fileName);
    if(required || file.exists())
    {
      InputStream is = new FileInputStream(file);
      if(required || is != null)
      {
        properties.load(is);
        is.close();
      }
    }
  }

  public static List<com.eviware.soapui.model.testsuite.TestCase[]> getTestCases(String projectFile,
    String projectPassword)
  {
    WsdlProject project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl").createNew(projectFile,
      projectPassword);
    List<com.eviware.soapui.model.testsuite.TestCase[]> testCases =
      new ArrayList<com.eviware.soapui.model.testsuite.TestCase[]>();
    if(project.isDisabled())
    {
      System.out.println("Skipping project : " + projectFile);
      return testCases;
    }
    for(TestSuite testSuite : project.getTestSuiteList())
    {
      for(com.eviware.soapui.model.testsuite.TestCase testCase : testSuite.getTestCaseList())
      {
        testCases.add(new com.eviware.soapui.model.testsuite.TestCase[]{testCase});
      }
    }
    return testCases;
  }

  private void disableSoapUILoggers()
  {
    Logger.getLogger("com.eviware").setLevel(Level.OFF);
    Logger.getRootLogger().removeAllAppenders();
  }

}

