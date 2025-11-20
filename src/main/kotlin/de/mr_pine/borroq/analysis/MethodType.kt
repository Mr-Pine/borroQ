package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.BorroQChecker
import org.checkerframework.framework.source.SourceVisitor

class MethodTypeAnalysis(checker: BorroQChecker): SourceVisitor<Unit, Unit>(checker) {
}
