import de.mr_pine.borroq.BorroQChecker
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
class BorroQTest(testFile: File?) : CheckerFrameworkPerFileTest(
    testFile,
    BorroQChecker::class.java,
    "de/mr_pine/borroq",
    "-Adetailedmsgtext",
    "-Astubs=stubs/",
    "-Aflowdotdir=build/cfgraphs/",
    "-nowarn"
) {
    companion object {
        @Suppress("unused")
        @JvmStatic
        @get:Parameterized.Parameters
        val testDirs: Array<String?>
            get() = arrayOf<String?>("borroq")
    }
}