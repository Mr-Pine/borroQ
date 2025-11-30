package de.mr_pine.borroq.types

import org.checkerframework.dataflow.cfg.node.LocalVariableNode
import org.checkerframework.dataflow.cfg.node.Node

@JvmInline
value class Id(val name: String): Borrow.Identifier {
    companion object {
        fun fromNode(node: Node) = when (node) {
            is LocalVariableNode -> Id(node.name.toString())
            else -> TODO()
        }
    }
}