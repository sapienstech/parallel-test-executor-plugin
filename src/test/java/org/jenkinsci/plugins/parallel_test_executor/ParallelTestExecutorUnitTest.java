package org.jenkinsci.plugins.parallel_test_executor;

import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import org.apache.tools.ant.DirectoryScanner;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ParallelTestExecutorUnitTest {

    ParallelTestExecutor instance;

    @Mock Run<?, ?> build;

    @Mock Run<?, ?> previousBuild;

    @Mock TaskListener listener;

    @Mock AbstractTestResultAction action;

    @Rule public TestName name = new TestName();

    File projectRootDir;

    DirectoryScanner scanner;
    private static final int NUM_OF_TEST_NODES_IN_DECISION_CI_WITHOUT_BDD_MACHINE = 9;


    @Before
    public void setUp() throws Exception {
        when(build.getPreviousBuild()).thenReturn((Run)previousBuild);
        when(previousBuild.getResult()).thenReturn(Result.SUCCESS);
        when(listener.getLogger()).thenReturn(System.err);
        when(previousBuild.getAction(eq(AbstractTestResultAction.class))).thenReturn(action);
    }

    @Before
    public void findProjectRoot() throws Exception {
        URL url = getClass().getResource(getClass().getSimpleName() + "/" + this.name.getMethodName());
        assumeThat("The test resource for " + this.name.getMethodName() + " exist", url, Matchers.notNullValue());
        try {
            projectRootDir = new File(url.toURI());
        } catch (URISyntaxException e) {
            projectRootDir = new File(url.getPath());
        }
        scanner = new DirectoryScanner();
        scanner.setBasedir(projectRootDir);
        scanner.scan();
    }

    @Test
    public void findTestSplits() throws Exception {
        TestResult testResult = new TestResult(0L, scanner, false);
        testResult.tally();
        when(action.getResult()).thenReturn(testResult);

        CountDrivenParallelism parallelism = new CountDrivenParallelism(5);
        List<InclusionExclusionPattern> splits = ParallelTestExecutor.findTestSplits(parallelism, build, listener, false);
        assertEquals(5, splits.size());
        for (InclusionExclusionPattern split : splits) {
            assertFalse(split.isIncludes());
        }
    }

    @Test
    public void decisionCiExecutionSimulation() throws Exception {
        TestResult testResult = new TestResult(0L, scanner, false);
        testResult.tally();
        when(action.getResult()).thenReturn(testResult);
        Set<String> bddTestClasses = extractBddTestClasses(testResult);

        CountDrivenParallelism parallelism = new CountDrivenParallelism(NUM_OF_TEST_NODES_IN_DECISION_CI_WITHOUT_BDD_MACHINE);
        List<InclusionExclusionPattern> splits = ParallelTestExecutor.findTestSplits(parallelism, build, listener, true);
        assertEquals(NUM_OF_TEST_NODES_IN_DECISION_CI_WITHOUT_BDD_MACHINE, splits.size());
        assertTrue(areResultingSplitsFreeOfBdd(splits,bddTestClasses));
    }

    private boolean areResultingSplitsFreeOfBdd(List<InclusionExclusionPattern> splits, Set<String> bddTestClasses) {
        return Collections.disjoint(getAllClassesInSplits(splits),bddTestClasses);
    }

    private Set<String> getAllClassesInSplits(List<InclusionExclusionPattern> splits) {
        Set<String> allClassesInSplit = new HashSet<>();
        String[] extensionsToRemove = {"java","class"};
        for (InclusionExclusionPattern split : splits) {
            allClassesInSplit.addAll(removeAllExtensions(split.getList(),extensionsToRemove));
        }
        return allClassesInSplit;
    }

    private Set<String> extractBddTestClasses(TestResult testResult) {
        Set<String> bddClasses = new HashSet<>();
        for(SuiteResult suite : testResult.getSuites()) {
            if (suite.getFile().toLowerCase().contains("bdd")) {
                bddClasses.addAll(suite.getClassNames());
            }
        }
        return bddClasses;
    }

    private List<String> removeAllExtensions(List<String> stringList, String[] extensionsToRemove) {
        List<String> stringsWithoutExtension = new ArrayList<>();
        for(String string : stringList) {
            for (String extension : extensionsToRemove) {
                stringsWithoutExtension.add(string.replace("." + extension,""));
            }
        }
        return stringsWithoutExtension;
    }

    @Test
    public void findTestSplitsInclusions() throws Exception {
        TestResult testResult = new TestResult(0L, scanner, false);
        testResult.tally();
        when(action.getResult()).thenReturn(testResult);

        CountDrivenParallelism parallelism = new CountDrivenParallelism(5);
        List<InclusionExclusionPattern> splits = ParallelTestExecutor.findTestSplits(parallelism, build, listener, true);
        assertEquals(5, splits.size());
        List<String> exclusions = new ArrayList<>(splits.get(0).getList());
        List<String> inclusions = new ArrayList<>();
        for (int i = 0; i < splits.size(); i++) {
            InclusionExclusionPattern split = splits.get(i);
            assertEquals(i != 0, split.isIncludes());
            if (split.isIncludes()) {
                inclusions.addAll(split.getList());
            }
        }
        Collections.sort(exclusions);
        Collections.sort(inclusions);
        assertEquals("exclusions set should contain all elements included by inclusions set", inclusions, exclusions);
    }
}
