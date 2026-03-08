package de.mr_pine.borroq.types

import org.checkerframework.dataflow.cfg.node.LocalVariableNode
import org.checkerframework.dataflow.cfg.node.Node

data class Id(val name: String, val nonce: Int): Borrow.Identifier {
    constructor(name: String): this(name, -1)

    companion object {
        fun fromNode(node: Node) = when (node) {
            is LocalVariableNode -> {
                println("Warning: Old id generation")
                Id(node.name.toString(), -1)
            }
            else -> TODO()
        }
    }
}