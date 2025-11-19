package de.mr_pine.borroq

import com.sun.source.tree.*
import org.checkerframework.framework.source.SourceVisitor

class BorroQVisitor(val checker: BorroQChecker, val strictness: Strictness, val typeQuery: TypeQuery = TypeQuery(checker)) : SourceVisitor<Unit, Unit>(checker) {

    fun unhandled(node: Tree) {
        if (strictness == Strictness.STRICT) {
            checker.reportError(node, Messages.UNKNOWN_TREE_ENCOUNTERED)
        } else {
            checker.reportWarning(node, Messages.UNKNOWN_TREE_ENCOUNTERED)
        }
    }

    override fun visitCompilationUnit(node: CompilationUnitTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitCompilationUnit(node, p)
    }

    override fun visitPackage(node: PackageTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitPackage(node, p)
    }

    override fun visitImport(node: ImportTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitImport(node, p)
    }

    override fun visitEmptyStatement(node: EmptyStatementTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitEmptyStatement(node, p)
    }

    override fun visitBlock(node: BlockTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitBlock(node, p)
    }

    override fun visitDoWhileLoop(node: DoWhileLoopTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitDoWhileLoop(node, p)
    }

    override fun visitWhileLoop(node: WhileLoopTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitWhileLoop(node, p)
    }

    override fun visitForLoop(node: ForLoopTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitForLoop(node, p)
    }

    override fun visitEnhancedForLoop(node: EnhancedForLoopTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitEnhancedForLoop(node, p)
    }

    override fun visitLabeledStatement(node: LabeledStatementTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitLabeledStatement(node, p)
    }

    override fun visitSwitch(node: SwitchTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitSwitch(node, p)
    }

    override fun visitSwitchExpression(node: SwitchExpressionTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitSwitchExpression(node, p)
    }

    override fun visitCase(node: CaseTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitCase(node, p)
    }

    override fun visitSynchronized(node: SynchronizedTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitSynchronized(node, p)
    }

    override fun visitTry(node: TryTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitTry(node, p)
    }

    override fun visitCatch(node: CatchTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitCatch(node, p)
    }

    override fun visitConditionalExpression(node: ConditionalExpressionTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitConditionalExpression(node, p)
    }

    override fun visitIf(node: IfTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitIf(node, p)
    }

    override fun visitExpressionStatement(node: ExpressionStatementTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitExpressionStatement(node, p)
    }

    override fun visitBreak(node: BreakTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitBreak(node, p)
    }

    override fun visitContinue(node: ContinueTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitContinue(node, p)
    }

    override fun visitReturn(node: ReturnTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitReturn(node, p)
    }

    override fun visitThrow(node: ThrowTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitThrow(node, p)
    }

    override fun visitAssert(node: AssertTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitAssert(node, p)
    }

    override fun visitMethodInvocation(node: MethodInvocationTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitMethodInvocation(node, p)
    }

    override fun visitNewClass(node: NewClassTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitNewClass(node, p)
    }

    override fun visitNewArray(node: NewArrayTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitNewArray(node, p)
    }

    override fun visitLambdaExpression(node: LambdaExpressionTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitLambdaExpression(node, p)
    }

    override fun visitParenthesized(node: ParenthesizedTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitParenthesized(node, p)
    }

    override fun visitAssignment(node: AssignmentTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitAssignment(node, p)
    }

    override fun visitCompoundAssignment(node: CompoundAssignmentTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitCompoundAssignment(node, p)
    }

    override fun visitUnary(node: UnaryTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitUnary(node, p)
    }

    override fun visitBinary(node: BinaryTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitBinary(node, p)
    }

    override fun visitTypeCast(node: TypeCastTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitTypeCast(node, p)
    }

    override fun visitInstanceOf(node: InstanceOfTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitInstanceOf(node, p)
    }

    override fun visitAnyPattern(node: AnyPatternTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitAnyPattern(node, p)
    }

    override fun visitBindingPattern(node: BindingPatternTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitBindingPattern(node, p)
    }

    override fun visitDefaultCaseLabel(node: DefaultCaseLabelTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitDefaultCaseLabel(node, p)
    }

    override fun visitConstantCaseLabel(node: ConstantCaseLabelTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitConstantCaseLabel(node, p)
    }

    override fun visitPatternCaseLabel(node: PatternCaseLabelTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitPatternCaseLabel(node, p)
    }

    override fun visitDeconstructionPattern(node: DeconstructionPatternTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitDeconstructionPattern(node, p)
    }

    override fun visitArrayAccess(node: ArrayAccessTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitArrayAccess(node, p)
    }

    override fun visitMemberSelect(node: MemberSelectTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitMemberSelect(node, p)
    }

    override fun visitMemberReference(node: MemberReferenceTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitMemberReference(node, p)
    }

    override fun visitIdentifier(node: IdentifierTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitIdentifier(node, p)
    }

    override fun visitLiteral(node: LiteralTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitLiteral(node, p)
    }

    override fun visitPrimitiveType(node: PrimitiveTypeTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitPrimitiveType(node, p)
    }

    override fun visitArrayType(node: ArrayTypeTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitArrayType(node, p)
    }

    override fun visitParameterizedType(node: ParameterizedTypeTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitParameterizedType(node, p)
    }

    override fun visitUnionType(node: UnionTypeTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitUnionType(node, p)
    }

    override fun visitIntersectionType(node: IntersectionTypeTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitIntersectionType(node, p)
    }

    override fun visitTypeParameter(node: TypeParameterTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitTypeParameter(node, p)
    }

    override fun visitWildcard(node: WildcardTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitWildcard(node, p)
    }

    override fun visitModifiers(node: ModifiersTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitModifiers(node, p)
    }

    override fun visitAnnotation(node: AnnotationTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitAnnotation(node, p)
    }

    override fun visitAnnotatedType(node: AnnotatedTypeTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitAnnotatedType(node, p)
    }

    override fun visitModule(node: ModuleTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitModule(node, p)
    }

    override fun visitExports(node: ExportsTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitExports(node, p)
    }

    override fun visitOpens(node: OpensTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitOpens(node, p)
    }

    override fun visitProvides(node: ProvidesTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitProvides(node, p)
    }

    override fun visitRequires(node: RequiresTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitRequires(node, p)
    }

    override fun visitUses(node: UsesTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitUses(node, p)
    }

    override fun visitOther(node: Tree?, p: Unit?) {
        unhandled(node!!)
        return super.visitOther(node, p)
    }

    override fun visitErroneous(node: ErroneousTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitErroneous(node, p)
    }

    override fun visitYield(node: YieldTree?, p: Unit?) {
        unhandled(node!!)
        return super.visitYield(node, p)
    }

    override fun visitClass(classTree: ClassTree?, p: Unit?) {
        unhandled(classTree!!)
        return super.visitClass(classTree, p)
    }

    override fun visitVariable(variableTree: VariableTree?, p: Unit?) {
        unhandled(variableTree!!)
        return super.visitVariable(variableTree, p)
    }

    override fun visitMethod(tree: MethodTree?, p: Unit?) {
        unhandled(tree!!)
        return super.visitMethod(tree, p)
    }
}