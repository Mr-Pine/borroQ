package de.mr_pine.borroq.types

import org.checkerframework.dataflow.cfg.node.ClassNameNode
import javax.lang.model.element.VariableElement

sealed interface PathRoot {
    @JvmInline
    value class StaticPathRoot(val className: ClassNameNode) : PathRoot

    @JvmInline
    value class IdPathRoot(val id: Id) : PathRoot
}

@JvmInline
value class PathTail(val fields: List<VariableElement>) {
    constructor(vararg fields: VariableElement) : this(fields.toList())

    fun isPrefixOf(other: PathTail) = fields.size <= other.fields.size && fields.zip(other.fields)
        .all { (a, b) -> a == b }

    fun with(element: VariableElement) = PathTail(fields + element)
}

data class Path(val root: PathRoot, val tail: PathTail) {
    constructor(root: PathRoot) : this(root, PathTail(emptyList()))
}
