import de.mr_pine.optionaldemo.OptionalDemoChecker
import org.checkerframework.framework.test.CheckerFrameworkPerFileTest
import org.checkerframework.framework.test.TestRootDirectory
import org.junit.runners.Parameterized
import java.io.File

/**
 * Test runner for tests of the Optional Demo Checker.
 *
 *
 * Tests appear as Java files in the `tests/optionaldemo` folder. To add a new test case,
 * create a Java file in that directory. The file contains "// ::" comments to indicate expected
 * errors and warnings; see
 * https://github.com/typetools/checker-framework/blob/master/checker/tests/README.
 */
@TestRootDirectory("src/test/java/testcases")
class OptionalDemoTest(testFile: File?) : CheckerFrameworkPerFileTest(
    testFile,
    OptionalDemoChecker::class.java,
    "de/mr_pine/optionaldemo",
    "-Anomsgtext",
    "-Astubs=stubs/",
    "-Aflowdotdir=build/cfgraphs/",
    "-nowarn"
) {
    companion object {
        @JvmStatic
        @get:Parameterized.Parameters
        val testDirs: Array<String?>
            get() = arrayOf<String?>("optionaldemo")
    }
}