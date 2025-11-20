package de.mr_pine.borroq

import de.mr_pine.borroq.types.IdentifiedPermission
import org.checkerframework.dataflow.analysis.Store
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable

class BorroQStore private constructor(val variablePermissions: MutableMap<LocalVariable, IdentifiedPermission>, val thisPermission: IdentifiedPermission, val borrowList: MutableList<Unit>): Store<BorroQStore> {

    constructor(): this(mutableMapOf(), TODO(), TODO())

    override fun copy() = BorroQStore(variablePermissions.toMutableMap(), thisPermission, borrowList.toMutableList())

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
        return true
    }

    override fun visualize(viz: CFGVisualizer<*, BorroQStore, *>?): String {
        TODO("Not yet implemented")
    }
}