package de.mr_pine.borroq

import de.mr_pine.borroq.types.IdentifiedPermission
import org.checkerframework.dataflow.analysis.Store
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable

class BorroQStore: Store<BorroQStore> {

    val variablePermissions: MutableMap<LocalVariable, IdentifiedPermission> = mutableMapOf()
    val thisPermission: IdentifiedPermission = TODO()

    val borrowList: MutableList<Unit> = TODO()

    override fun copy(): BorroQStore {
        TODO("Not yet implemented")
    }

    override fun leastUpperBound(other: BorroQStore?): BorroQStore {
        TODO("Not yet implemented")
    }

    override fun widenedUpperBound(previous: BorroQStore?): BorroQStore {
        TODO("Not yet implemented")
    }

    override fun canAlias(
        a: JavaExpression?,
        b: JavaExpression?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun visualize(viz: CFGVisualizer<*, BorroQStore, *>?): String {
        TODO("Not yet implemented")
    }
}