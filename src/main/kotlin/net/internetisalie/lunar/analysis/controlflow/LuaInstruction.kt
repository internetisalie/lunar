package net.internetisalie.lunar.analysis.controlflow

import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.intellij.psi.PsiElement

interface LuaInstruction : Instruction

enum class AccessType {
    READ, WRITE
}

class LuaReadWriteInstruction(
    builder: ControlFlowBuilder,
    element: PsiElement,
    val variableName: String,
    val accessType: AccessType
) : InstructionImpl(builder, element), LuaInstruction {
    override fun getElementPresentation(): String {
        return "$accessType $variableName"
    }
}

class LuaBranchInstruction(
    builder: ControlFlowBuilder,
    element: PsiElement?
) : InstructionImpl(builder, element), LuaInstruction
