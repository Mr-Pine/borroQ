package de.mr_pine.borroq.types

import org.checkerframework.dataflow.expression.LocalVariable

sealed interface PathRoot {
    data object ThisPathRoot : PathRoot

    @JvmInline
    value class LocalVariableRoot(val variable: LocalVariable) : PathRoot
}

data class PathTail(val fields: List<String>)

data class Path(val root: PathRoot, val tail: PathTail)
data class IdPath(val id: Id, val tail: PathTail)