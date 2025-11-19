package tests;

import de.mr_pine.optionaldemo.OptionalDemoChecker;
import org.checkerframework.framework.test.CheckerFrameworkPerFileTest;
import org.checkerframework.framework.test.TestRootDirectory;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;

/**
 * Test runner for tests of the Optional Demo Checker.
 *
 * <p>Tests appear as Java files in the {@code tests/optionaldemo} folder. To add a new test case,
 * create a Java file in that directory. The file contains "// ::" comments to indicate expected
 * errors and warnings; see
 * https://github.com/typetools/checker-framework/blob/master/checker/tests/README .
 */
@TestRootDirectory("src/test/java/testcases")
public class OptionalDemoTest extends CheckerFrameworkPerFileTest {
    public OptionalDemoTest(File testFile) {
        super(
                testFile,
                OptionalDemoChecker.class,
                "optionaldemo",
                "-Anomsgtext",
                "-Astubs=stubs/",
                "-nowarn");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"optionaldemo"};
    }
}