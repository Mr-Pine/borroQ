package de.mr_pine.borroq

import org.checkerframework.framework.source.SourceChecker
import org.checkerframework.framework.source.SourceVisitor
import org.checkerframework.framework.source.SupportedOptions

@SupportedOptions(Strictness.KEY)
class BorroQChecker: SourceChecker() {


    override fun createSourceVisitor(): SourceVisitor<*, *> {
        val strictness = getOption(Strictness.KEY, Strictness.DEFAULT).let(Strictness::fromString)
        getTreePathCacher()

        return BorroQVisitor(this, strictness)
    }


}