package com.github.erjotek.stickyprojectfolder.editor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class JavaStickyBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        val java = Language.findLanguageByID("JAVA") ?: return emptyArray()
        return arrayOf(java)
    }

    override fun acceptElement(element: PsiElement): Boolean {
        return classifyJava(element) != null
    }

    @Suppress("UnstableApiUsage")
    override fun acceptStickyElement(element: PsiElement): Boolean {
        val kind = classifyJava(element) ?: return false
        return isKindEnabledForSticky(kind)
    }

    override fun getElementInfo(element: PsiElement): String {
        val kind = classifyJava(element) ?: return ""
        return labelFor(element, kind)
    }
}

private val JAVA_SIMPLE_NAME_KIND: Map<String, StickyKind> = mapOf(
    "PsiClass" to StickyKind.CLASS_OR_FUNCTION,
    "PsiAnonymousClass" to StickyKind.CLASS_OR_FUNCTION,
    "PsiMethod" to StickyKind.CLASS_OR_FUNCTION,
    "PsiLambdaExpression" to StickyKind.CLASS_OR_FUNCTION,
    "PsiIfStatement" to StickyKind.IF,
    "PsiForStatement" to StickyKind.FOR,
    "PsiForeachStatement" to StickyKind.FOREACH,
    "PsiWhileStatement" to StickyKind.WHILE,
    "PsiDoWhileStatement" to StickyKind.DO,
    "PsiSwitchStatement" to StickyKind.SWITCH,
    "PsiSwitchExpression" to StickyKind.SWITCH,
    "PsiTryStatement" to StickyKind.TRY,
    "PsiCatchSection" to StickyKind.CATCH
)

private fun classifyJava(element: PsiElement): StickyKind? =
    refineJavaKind(element, classifyBySimpleName(element, JAVA_SIMPLE_NAME_KIND))

private fun refineJavaKind(element: PsiElement, baseKind: StickyKind?): StickyKind? {
    val simple = simpleNameWithoutImpl(element)
    val parent = element.parent ?: return baseKind
    val parentSimple = parentSimpleName(element) ?: return baseKind

    if (parentSimple == "PsiIfStatement") {
        val elseBranch = invokeNoArg(parent, "getElseBranch")
        if (elseBranch === element) {
            if (simple == "PsiIfStatement") return StickyKind.ELSEIF
            if (simple == "PsiBlockStatement") return StickyKind.ELSE
        }
    }
    return baseKind
}
