package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.analysis.exceptions.InsufficientPermissionException
import de.mr_pine.borroq.analysis.exceptions.TopPermissionEncounteredException
import de.mr_pine.borroq.types.*
import org.checkerframework.dataflow.analysis.Store
import org.checkerframework.dataflow.cfg.node.Node
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable

class BorroQStore private constructor(
    private val variablePermissions: MutableMap<LocalVariable, VariablePermission>,
    private var thisPermission: VariablePermission,
    private val borrowList: MutableList<Unit>
) : Store<BorroQStore> {

    constructor() : this(
        mutableMapOf(), IdentifiedPermission(Rational.ONE, Id("this")), mutableListOf()
    )

    override fun copy() = BorroQStore(variablePermissions.toMutableMap(), thisPermission, borrowList.toMutableList())

    fun updatePermission(target: Node, permission: VariablePermission) {
        when (val expression = JavaExpression.fromNode(target)) {
            is LocalVariable -> variablePermissions[expression] = permission
            else -> TODO()
        }
    }

    fun updateThisPermission(permission: VariablePermission) {
        thisPermission = permission
    }

    @Throws(InsufficientPermissionException::class, TopPermissionEncounteredException::class)
    fun chooseAndRemoveArgumentPermission(argument: Node, parameterMutability: Mutability): VariablePermission {
        val availablePermission = when (val expression = JavaExpression.fromNode(argument)) {
            is LocalVariable -> variablePermissions[expression]!!
            else -> TODO("non local-var argument")
        }

        val (split, remaining) = when (availablePermission) {
            is VariablePermission.Top -> throw TopPermissionEncounteredException(argument.toString())
            is IdentifiedPermission -> {
                when (parameterMutability) {
                    Mutability.MUTABLE -> if (!availablePermission.isMutable) throw InsufficientPermissionException(
                        argument.toString(), Mutability.MUTABLE, availablePermission
                    )

                    Mutability.IMMUTABLE -> if (!availablePermission.isReadable) throw InsufficientPermissionException(
                        argument.toString(), Mutability.IMMUTABLE, availablePermission
                    )
                }

                availablePermission.split(parameterMutability)
            }
        }

        updatePermission(argument, remaining)

        return split
    }

    @Throws(InsufficientPermissionException::class, TopPermissionEncounteredException::class)
    fun chooseAndRemoveThisReceiverPermission(mutability: Mutability): VariablePermission {
        val availablePermission = thisPermission

        val (split, remaining) = when (availablePermission) {
            is VariablePermission.Top -> throw TopPermissionEncounteredException("this")
            is IdentifiedPermission -> {
                when (mutability) {
                    Mutability.MUTABLE -> if (!availablePermission.isMutable) throw InsufficientPermissionException(
                        "this", Mutability.MUTABLE, availablePermission
                    )

                    Mutability.IMMUTABLE -> if (!availablePermission.isReadable) throw InsufficientPermissionException(
                        "this", Mutability.IMMUTABLE, availablePermission
                    )
                }

                availablePermission.split(mutability)
            }
        }

        updateThisPermission(remaining)

        return split
    }

    fun recombine(receiver: LocalVariable, permission: VariablePermission) {
        val existingPermission = variablePermissions[receiver]!!
        require(existingPermission is IdentifiedPermission) { "Cannot recombine with non-identified permission" }
        require(permission is IdentifiedPermission) { "Cannot recombine non-identified permission" }
        require(existingPermission.id == permission.id) { "Cannot recombine permissions with different ids" }
        variablePermissions[receiver] = existingPermission.recombineFractional(permission)
    }

    fun recombine(receiver: Node, permission: VariablePermission) {
        when (val expression = JavaExpression.fromNode(receiver)) {
            is LocalVariable -> recombine(expression, permission)
            else -> TODO()
        }
    }

    fun recombineThis(permission: VariablePermission) {
        require(thisPermission is IdentifiedPermission) { "Cannot recombine with non-identified permission" }
        require(permission is IdentifiedPermission) { "Cannot recombine non-identified permission" }
        require((thisPermission as IdentifiedPermission).id == permission.id) { "Cannot recombine permissions with different ids" }
        thisPermission = (thisPermission as IdentifiedPermission).recombineFractional(permission)
    }

    fun killVariable(liveVariable: Node) {
        when (val expression = JavaExpression.fromNode(liveVariable)) {
            is LocalVariable -> {
                val permission = variablePermissions[expression]!!
                if (permission !is IdentifiedPermission) {
                    return
                }
                val id = permission.id
                val otherVariables =
                    variablePermissions.entries.filterIsInstance<Map.Entry<LocalVariable, IdentifiedPermission>>()
                        .filter { (key, _) -> key != expression }
                        .filter { (_, value) -> value.id == id }
                        .map(Map.Entry<LocalVariable, IdentifiedPermission>::key)
                val otherVariable = otherVariables.firstOrNull() ?: return
                variablePermissions[expression] = Permission(Rational.ZERO).withId(id)
                recombine(otherVariable, permission)
            }

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