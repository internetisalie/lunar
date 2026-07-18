package net.internetisalie.lunar.analysis.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.*

class LuaControlFlowBuilder : LuaVisitor() {
    private val builder = ControlFlowBuilder()
    private val breakStack = mutableListOf<MutableList<Instruction>>()
    private val returnInstructions = mutableListOf<Instruction>()
    
    private data class GotoRecord(val gotoInst: Instruction, val targetName: String, val gotoElement: PsiElement)
    private data class LabelKey(val name: String, val block: LuaBlock)
    private val gotoInstructions = mutableListOf<GotoRecord>()
    private val labelInstructions = mutableMapOf<LabelKey, Instruction>()
    
    private var isAbrupted = false

    fun build(owner: ScopeOwner): ControlFlow {
        builder.startNode(owner)
        
        when (owner) {
            is LuaFuncDecl -> {
                owner.getParList()?.getNameList()?.getNameRefList()?.forEach { param ->
                    builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, param, param.text, AccessType.WRITE))
                }
                owner.getBlockList().firstOrNull()?.accept(this)
            }
            is LuaLocalFuncDecl -> {
                owner.getParList()?.getNameList()?.getNameRefList()?.forEach { param ->
                    builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, param, param.text, AccessType.WRITE))
                }
                owner.getBlockList().firstOrNull()?.accept(this)
            }
            is LuaFuncDef -> {
                owner.getParList()?.getNameList()?.getNameRefList()?.forEach { param ->
                    builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, param, param.text, AccessType.WRITE))
                }
                owner.getBlockList().firstOrNull()?.accept(this)
            }
            is LuaFile -> {
                for (block in owner.getBlockList()) {
                    block.accept(this)
                }
            }
            is LuaBlock -> {
                owner.accept(this)
            }
        }
        
        val exitInstruction = builder.startNode(null)
        
        for (ret in returnInstructions) {
            builder.addEdge(ret, exitInstruction)
        }
        
        for (gotoRec in gotoInstructions) {
            val targetLabel = resolveGoto(gotoRec)
            if (targetLabel != null) {
                builder.addEdge(gotoRec.gotoInst, targetLabel)
            }
        }

        builder.completeControlFlow()
        return LuaControlFlowImpl(builder.controlFlow.instructions)
    }

    private fun resolveGoto(record: GotoRecord): Instruction? {
        var block = PsiTreeUtil.getParentOfType(record.gotoElement, LuaBlock::class.java)
        while (block != null) {
            labelInstructions[LabelKey(record.targetName, block)]?.let { return it }
            block = PsiTreeUtil.getParentOfType(block, LuaBlock::class.java)
        }
        return null
    }

    override fun visitBlock(block: LuaBlock) {
        for (stat in block.getStatementList()) {
            if (isAbrupted) {
                builder.flowAbrupted()
            }
            if (stat !is LuaIfStatement &&
                stat !is LuaWhileStatement &&
                stat !is LuaRepeatStatement &&
                stat !is LuaNumericForStatement &&
                stat !is LuaGenericForStatement &&
                stat !is LuaBreakStatement &&
                stat !is LuaFinalStatement &&
                stat !is LuaGotoStatement &&
                stat !is LuaLabel
            ) {
                builder.startNode(stat)
            }
            stat.accept(this)
        }
    }

    override fun visitIfStatement(ifStatement: LuaIfStatement) {
        val exprList = ifStatement.getExprList()
        val blockList = ifStatement.getBlockList()
        if (blockList.isEmpty()) return
        
        val branchAbruptedList = mutableListOf<Boolean>()
        var prevCondInstruction: Instruction? = null
        
        for (i in blockList.indices) {
            val expr = exprList.getOrNull(i)
            if (expr != null) {
                val condInst = builder.startNode(expr)
                if (prevCondInstruction != null) {
                    builder.addEdge(prevCondInstruction, condInst)
                }
                expr.accept(this)

                isAbrupted = false
                blockList[i].accept(this)
                branchAbruptedList.add(isAbrupted)

                val fallThrough = builder.prevInstruction
                if (!isAbrupted && fallThrough != null) {
                    builder.addPendingEdge(ifStatement, fallThrough)
                }
                builder.flowAbrupted()
                prevCondInstruction = condInst
            } else {
                val elseInst = builder.startNode(blockList[i])
                if (prevCondInstruction != null) {
                    builder.addEdge(prevCondInstruction, elseInst)
                }

                isAbrupted = false
                blockList[i].accept(this)
                branchAbruptedList.add(isAbrupted)

                val fallThrough = builder.prevInstruction
                if (!isAbrupted && fallThrough != null) {
                    builder.addPendingEdge(ifStatement, fallThrough)
                }
                builder.flowAbrupted()
                prevCondInstruction = null
            }
        }
        
        if (prevCondInstruction != null) {
            builder.addPendingEdge(ifStatement, prevCondInstruction)
            branchAbruptedList.add(false)
        }
        
        isAbrupted = branchAbruptedList.all { it }
    }

    override fun visitWhileStatement(whileStatement: LuaWhileStatement) {
        val condInst = builder.startNode(whileStatement.getExpr())
        whileStatement.getExpr()?.accept(this)
        breakStack.add(mutableListOf())

        isAbrupted = false
        whileStatement.getBlockList().firstOrNull()?.accept(this)

        val fallThrough = builder.prevInstruction
        if (!isAbrupted && fallThrough != null) {
            builder.addEdge(fallThrough, condInst)
        }
        builder.flowAbrupted()
        
        val breaks = breakStack.removeAt(breakStack.size - 1)
        for (br in breaks) {
            builder.addPendingEdge(whileStatement, br)
        }
        builder.addPendingEdge(whileStatement, condInst)
        
        isAbrupted = false
    }

    override fun visitRepeatStatement(repeatStatement: LuaRepeatStatement) {
        val startInst = builder.startNode(repeatStatement)
        breakStack.add(mutableListOf())
        
        isAbrupted = false
        repeatStatement.getBlockList().firstOrNull()?.accept(this)
        
        val cond = repeatStatement.getExpr()
        val condInst = builder.startNode(cond)
        cond?.accept(this)
        if (!isAbrupted) {
            builder.addEdge(condInst, startInst)
        }
        
        val breaks = breakStack.removeAt(breakStack.size - 1)
        for (br in breaks) {
            builder.addPendingEdge(repeatStatement, br)
        }
        
        isAbrupted = false
    }

    override fun visitNumericForStatement(numericForStatement: LuaNumericForStatement) {
        numericForStatement.getExprList().forEach { it.accept(this) }
        
        val loopInst = builder.startNode(numericForStatement)
        val id = numericForStatement.getIdentifier()
        builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, id, id.text, AccessType.WRITE))
        
        breakStack.add(mutableListOf())
        isAbrupted = false
        numericForStatement.getBlockList().firstOrNull()?.accept(this)

        val fallThrough = builder.prevInstruction
        if (!isAbrupted && fallThrough != null) {
            builder.addEdge(fallThrough, loopInst)
        }
        builder.flowAbrupted()
        
        val breaks = breakStack.removeAt(breakStack.size - 1)
        for (br in breaks) {
            builder.addPendingEdge(numericForStatement, br)
        }
        builder.addPendingEdge(numericForStatement, loopInst)
        
        isAbrupted = false
    }

    override fun visitGenericForStatement(genericForStatement: LuaGenericForStatement) {
        genericForStatement.getExprList().accept(this)
        
        val loopInst = builder.startNode(genericForStatement)
        genericForStatement.getNameList().getNameRefList().forEach { nameRef ->
            builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, nameRef, nameRef.text, AccessType.WRITE))
        }
        
        breakStack.add(mutableListOf())
        isAbrupted = false
        genericForStatement.getBlockList().firstOrNull()?.accept(this)

        val fallThrough = builder.prevInstruction
        if (!isAbrupted && fallThrough != null) {
            builder.addEdge(fallThrough, loopInst)
        }
        builder.flowAbrupted()
        
        val breaks = breakStack.removeAt(breakStack.size - 1)
        for (br in breaks) {
            builder.addPendingEdge(genericForStatement, br)
        }
        builder.addPendingEdge(genericForStatement, loopInst)
        
        isAbrupted = false
    }

    override fun visitDoStatement(doStatement: LuaDoStatement) {
        doStatement.getBlockList().firstOrNull()?.accept(this)
    }

    override fun visitBreakStatement(breakStatement: LuaBreakStatement) {
        val breakInst = builder.startNode(breakStatement)
        breakStack.lastOrNull()?.add(breakInst)
        builder.flowAbrupted()
        isAbrupted = true
    }

    override fun visitFinalStatement(finalStatement: LuaFinalStatement) {
        finalStatement.acceptChildren(this)
        val retInst = builder.startNode(finalStatement)
        returnInstructions.add(retInst)
        builder.flowAbrupted()
        isAbrupted = true
    }

    override fun visitGotoStatement(gotoStatement: LuaGotoStatement) {
        val gotoInst = builder.startNode(gotoStatement)
        val labelRef = gotoStatement.getLabelRef()
        gotoInstructions.add(GotoRecord(gotoInst, labelRef.text, gotoStatement))
        builder.flowAbrupted()
        isAbrupted = true
    }

    override fun visitLabel(label: LuaLabel) {
        val labelInst = builder.startNode(label)
        val labelName = label.getLabelName()
        val enclosingBlock = PsiTreeUtil.getParentOfType(label, LuaBlock::class.java)
        if (enclosingBlock != null) {
            labelInstructions[LabelKey(labelName.text, enclosingBlock)] = labelInst
        }
        isAbrupted = false
    }

    override fun visitLocalVarDecl(localVarDecl: LuaLocalVarDecl) {
        localVarDecl.getExprList()?.accept(this)
        localVarDecl.getAttNameList().forEach { attName ->
            val nameRef = attName.getNameRef()
            builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, nameRef, nameRef.text, AccessType.WRITE))
        }
    }

    override fun visitAssignmentStatement(assignmentStatement: LuaAssignmentStatement) {
        assignmentStatement.getExprList().accept(this)
        assignmentStatement.getVarList().getVarList().forEach { v ->
            val nameRef = v.getNameRef()
            if (nameRef != null && v.getVarSuffixList().isEmpty()) {
                builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, nameRef, nameRef.text, AccessType.WRITE))
            } else {
                v.accept(this)
            }
        }
    }

    override fun visitVar(o: LuaVar) {
        val nameRef = o.getNameRef()
        if (nameRef != null) {
            builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, nameRef, nameRef.text, AccessType.READ))
        }
        o.getVarSuffixList().forEach { it.accept(this) }
    }

    override fun visitFuncDecl(funcDecl: LuaFuncDecl) {
        val funcName = funcDecl.getFuncName()
        builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, funcName, funcName.text, AccessType.WRITE))
    }

    override fun visitLocalFuncDecl(localFuncDecl: LuaLocalFuncDecl) {
        val nameRef = localFuncDecl.getNameRef()
        builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, nameRef, nameRef.text, AccessType.WRITE))
    }

    override fun visitFuncDef(funcDef: LuaFuncDef) {
        // Anonymous function expression
    }

    override fun visitNameRef(nameRef: LuaNameRef) {
        val parent = nameRef.parent
        if (parent !is LuaVar &&
            parent !is LuaAttName &&
            parent !is LuaFuncName &&
            parent !is LuaLocalFuncDecl &&
            parent !is LuaFuncNameProperty &&
            parent !is LuaFuncNameMethod &&
            parent !is LuaMethodExpr &&
            parent !is LuaIndexExpr &&
            parent !is LuaNameList
        ) {
            builder.addNodeAndCheckPending(LuaReadWriteInstruction(builder, nameRef, nameRef.text, AccessType.READ))
        }
    }

    override fun visitPsiElement(element: PsiElement) {
        element.acceptChildren(this)
    }
}
