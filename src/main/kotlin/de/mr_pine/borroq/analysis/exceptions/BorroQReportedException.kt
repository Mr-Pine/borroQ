package de.mr_pine.borroq.analysis.exceptions

open class BorroQReportedException(override val cause: BorroQException) : Exception()