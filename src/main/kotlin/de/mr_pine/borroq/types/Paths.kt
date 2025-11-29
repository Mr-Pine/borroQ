package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.BorroQStore
import org.checkerframework.dataflow.expression.LocalVariable
import javax.lang.model.element.VariableElement

sealed interface PathRoot {
    data object ThisPathRoot : PathRoot

    @JvmInline
    value class LocalVariableRoot(val variable: LocalVariable) : PathRoot

    context(store: BorroQStore)
    fun toId() = when (this) {
        is ThisPathRoot -> (store.queryThisPermission() as IdentifiedPermission).id
        is LocalVariableRoot -> (store.queryPermission(variable) as IdentifiedPermission).id
    }
}

data class PathTail(val fields: List<VariableElement>)

data class Path(val root: PathRoot, val tail: PathTail) {
    constructor(root: PathRoot) : this(root, PathTail(emptyList()))

    context(store: BorroQStore)
    fun asIdPath() = IdPath(root.toId(), tail)

    fun with(field: VariableElement) = Path(root, tail.copy(fields = tail.fields + field))
}

data class IdPath(val id: Id, val tail: PathTail) {
    constructor(id: Id) : this(id, PathTail(emptyList()))

    fun with(field: VariableElement) = IdPath(id, tail.copy(fields = tail.fields + field))
}