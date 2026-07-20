import de.mr_pine.borroq.BorroQChecker
import org.checkerframework.framework.test.TestConfigurationBuilder
import org.checkerframework.framework.test.TestUtilities
import org.checkerframework.framework.test.TypecheckExecutor
import org.checkerframework.framework.test.TypecheckResult
import org.junit.runners.Parameterized
import java.io.File
import kotlin.test.Test


abstract class ArtefactEvaluationTest(vararg additionalOptions: String) {
    val checkerOptions = listOf(
        //"-Adetailedmsgtext",
        //"-Astrictness=allow-unknown",
        "-Astubs=stubs/",
        "-Aflowdotdir=build/cfgraphs/",
        "-nowarn"
    ) + additionalOptions
    val checker = BorroQChecker::class.java

    @Test
    fun run() {
        val shouldEmitDebugInfo = TestUtilities.getShouldEmitDebugInfo()

        val config =
            TestConfigurationBuilder.buildDefaultConfiguration(
                "src/test/java/testcases/artefactEvaluation",
                File("src/test/java/testcases/artefactEvaluation/${this::class.simpleName!!.removeSuffix("Test")}.java"),
                checker,
                checkerOptions,
                shouldEmitDebugInfo
            )
        val testResult = TypecheckExecutor().runTest(config)
        checkResult(testResult)
    }


    /**
     * Check that the [TypecheckResult] did not fail.
     *
     * @param typecheckResult result to check
     */
    fun checkResult(typecheckResult: TypecheckResult) {
        TestUtilities.assertTestDidNotFail(typecheckResult)
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        @get:Parameterized.Parameters
        val testFiles: List<File?>
            get() = listOf(File(this::class.toString()))
    }
}

class ImmutabilityTest : ArtefactEvaluationTest()
class MutabilityTest : ArtefactEvaluationTest()
class ImmutableReturnTest : ArtefactEvaluationTest()
class AliasDetectionTest : ArtefactEvaluationTest()
class DeepFieldTest : ArtefactEvaluationTest()
class MutableArgumentsTest : ArtefactEvaluationTest()
class BorrowedArgumentsTest : ArtefactEvaluationTest()
class RecombinationTest : ArtefactEvaluationTest()
class FlexibleArgumentExtensionTest : ArtefactEvaluationTest()
class ArrayExtensionTest : ArtefactEvaluationTest()
class ControlFlowExtensionTest : ArtefactEvaluationTest()
class ExtensionDisablingTest : ArtefactEvaluationTest("-Aborroq_extensions=disabled")
