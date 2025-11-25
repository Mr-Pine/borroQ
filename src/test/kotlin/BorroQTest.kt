import de.mr_pine.borroq.BorroQChecker
import org.checkerframework.framework.test.CheckerFrameworkPerFileTest
import org.checkerframework.framework.test.TestRootDirectory
import org.junit.runners.Parameterized
import java.io.File

@TestRootDirectory("src/test/java/testcases")
class BorroQTest(testFile: File?) : CheckerFrameworkPerFileTest(
    testFile,
    BorroQChecker::class.java,
    "de/mr_pine/borroq",
    //"-Adetailedmsgtext",
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