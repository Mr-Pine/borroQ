package de.mr_pine.borroq

import de.mr_pine.borroq.analysis.Configuration
import de.mr_pine.borroq.analysis.Configuration.Companion.getConfig
import org.checkerframework.framework.source.SourceChecker
import org.checkerframework.framework.source.SourceVisitor
import org.checkerframework.framework.source.SupportedOptions

@SupportedOptions(Configuration.BorrowQExtensions.KEY, Configuration.UnknownSyntaxStrictness.KEY)
class BorroQChecker : SourceChecker() {


    override fun createSourceVisitor(): SourceVisitor<*, *> {
        val configuration = getConfig()
        getTreePathCacher()

        return BorroQVisitor(this, configuration)
    }
}