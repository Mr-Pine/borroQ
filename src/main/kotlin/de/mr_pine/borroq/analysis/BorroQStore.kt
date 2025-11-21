package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.*
import org.checkerframework.dataflow.analysis.Store
import org.checkerframework.dataflow.cfg.node.Node
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable

class BorroQStore private constructor(
    private val variablePermissions: MutableMap<LocalVariable, VariablePermission>,
    private val thisPermission: VariablePermission,
    private val borrowList: MutableList<Unit>
) : Store<BorroQStore> {

    constructor() : this(
        mutableMapOf(), IdentifiedPermission(Permission.Rational.ONE, Id("this")), mutableListOf()
    )

    override fun copy() = BorroQStore(variablePermissions.toMutableMap(), thisPermission, borrowList.toMutableList())

    fun updatePermission(target: Node, permission: VariablePermission) {
        when (val expression = JavaExpression.fromNode(target)) {
            is LocalVariable -> variablePermissions[expression] = permission
            else -> TODO()
        }
    }

    fun queryPermission(target: Node): VariablePermission? {
        return when (val expression = JavaExpression.fromNode(target)) {
            is LocalVariable -> variablePermissions[expression]
            else -> TODO()
        }
    }

    override fun leastUpperBound(other: BorroQStore?): BorroQStore {
        other!!
        val combinedLocalPermissions = variablePermissions.keys.union(other.variablePermissions.keys).associateWith {
            val permissionA = variablePermissions[it]!!
            val permissionB = variablePermissions[it]!!

            permissionA.combine(permissionB)
        }
        val combinedThisPermission = thisPermission.combine(other.thisPermission)

        require(borrowList.isEmpty() && other.borrowList.isEmpty()) { "Non-empty borrows are not yet supported" }

        return BorroQStore(combinedLocalPermissions.toMutableMap(), combinedThisPermission, mutableListOf())
    }

    override fun widenedUpperBound(previous: BorroQStore?): BorroQStore {
        TODO("Not yet implemented")
    }

    override fun canAlias(
        a: JavaExpression?, b: JavaExpression?
    ): Boolean {
        return true
    }

    override fun visualize(viz: CFGVisualizer<*, BorroQStore, *>?): String {
        viz!! as CFGVisualizer<PermissionValue, BorroQStore, *>
        val values = buildList {
            variablePermissions.entries.sortedBy { it.key.toString() }
                .forEach { (variable, permission) -> add(viz.visualizeStoreLocalVar(variable, permission)) }
            add(viz.visualizeStoreThisVal(thisPermission))
        }.joinToString(viz.separator)

        return if (values.trim().isEmpty()) {
            "${this::class.simpleName}()"
        } else {
            "${this::class.simpleName}(${viz.separator}$values${viz.separator})"
        }
    }
}