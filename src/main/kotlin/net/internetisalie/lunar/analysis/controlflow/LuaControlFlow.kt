package net.internetisalie.lunar.analysis.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInsight.controlflow.impl.ControlFlowImpl
import com.intellij.psi.PsiElement

typealias ScopeOwner = PsiElement

interface LuaControlFlow : ControlFlow {
    fun isReachable(instruction: Instruction): Boolean
}

class LuaControlFlowImpl(
    instructions: Array<Instruction>
) : ControlFlowImpl(instructions), LuaControlFlow {
    override fun isReachable(instruction: Instruction): Boolean {
        val start = instructions.firstOrNull() ?: return false
        val visited = mutableSetOf<Instruction>()
        val queue = ArrayDeque<Instruction>()
        queue.add(start)
        visited.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == instruction) return true
            for (succ in current.allSucc()) {
                val succInst = succ as Instruction
                if (visited.add(succInst)) {
                    queue.add(succInst)
                }
            }
        }
        return false
    }
}

