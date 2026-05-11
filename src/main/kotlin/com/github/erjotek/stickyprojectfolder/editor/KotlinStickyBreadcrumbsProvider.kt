package com.github.erjotek.stickyprojectfolder.editor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class KotlinStickyBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        val kotlin = Language.findLanguageByID("kotlin")
            ?: Language.findLanguageByID("Kotlin")
            ?: Language.getRegisteredLanguages().firstOrNull { it.id.equals("kotlin", ignoreCase = true) }
        if (kotlin == null) return emptyArray()
        return arrayOf(kotlin)
    }

    override fun acceptElement(element: PsiElement): Boolean {
        return classifyKotlin(element) != null
    }

    @Suppress("UnstableApiUsage")
    override fun acceptStickyElement(element: PsiElement): Boolean {
        val kind = classifyKotlin(element) ?: return false
        return isKindEnabledForSticky(kind)
    }

    override fun getElementInfo(element: PsiElement): String {
        val kind = classifyKotlin(element) ?: return ""
        return labelFor(element, kind)
    }
}

private val KOTLIN_SIMPLE_NAME_KIND: Map<String, StickyKind> = mapOf(
    "KtClass" to StickyKind.CLASS_OR_FUNCTION,
    "KtObjectDeclaration" to StickyKind.CLASS_OR_FUNCTION,
    "KtNamedFunction" to StickyKind.CLASS_OR_FUNCTION,
    "KtFunctionLiteral" to StickyKind.CLASS_OR_FUNCTION,
    "KtIfExpression" to StickyKind.IF,
    "KtForExpression" to StickyKind.FOREACH,
    "KtWhileExpression" to StickyKind.WHILE,
    "KtDoWhileExpression" to StickyKind.DO,
    "KtWhenExpression" to StickyKind.SWITCH,
    "KtTryExpression" to StickyKind.TRY,
    "KtCatchClause" to StickyKind.CATCH,
    "KtFinallySection" to StickyKind.FINALLY
)

private val KOTLIN_ELEMENT_TYPE_KIND: Map<String, StickyKind> = mapOf(
    "CLASS" to StickyKind.CLASS_OR_FUNCTION,
    "OBJECT_DECLARATION" to StickyKind.CLASS_OR_FUNCTION,
    "FUN" to StickyKind.CLASS_OR_FUNCTION,
    "FUNCTION_LITERAL" to StickyKind.CLASS_OR_FUNCTION,
    "IF" to StickyKind.IF,
    "FOR" to StickyKind.FOREACH,
    "WHILE" to StickyKind.WHILE,
    "DO_WHILE" to StickyKind.DO,
    "WHEN" to StickyKind.SWITCH,
    "TRY" to StickyKind.TRY,
    "CATCH" to StickyKind.CATCH,
    "FINALLY" to StickyKind.FINALLY
)

private fun classifyKotlin(element: PsiElement): StickyKind? =
    refineKotlinKind(
        element,
        classifyBySimpleName(element, KOTLIN_SIMPLE_NAME_KIND)
            ?: classifyKotlinByElementType(element)
    )

private fun classifyKotlinByElementType(element: PsiElement): StickyKind? {
    val type = element.node?.elementType?.toString() ?: return null
    return KOTLIN_ELEMENT_TYPE_KIND[type]
}

private fun refineKotlinKind(element: PsiElement, baseKind: StickyKind?): StickyKind? {
    val simple = simpleNameWithoutImpl(element)
    val parent = element.parent ?: return baseKind
    val parentSimple = parentSimpleName(element) ?: return baseKind

    if (parentSimple == "KtIfExpression") {
        val elseBranch = invokeNoArg(parent, "getElse")
        if (elseBranch === element) {
            if (simple == "KtIfExpression") return StickyKind.ELSEIF
            if (simple == "KtBlockExpression") return StickyKind.ELSE
        }
    }
    return baseKind
}
