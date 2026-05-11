package com.github.erjotek.stickyprojectfolder.editor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class PythonStickyBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        val py = Language.findLanguageByID("Python") ?: return emptyArray()
        return arrayOf(py)
    }

    override fun acceptElement(element: PsiElement): Boolean {
        return classifyPython(element) != null
    }

    @Suppress("UnstableApiUsage")
    override fun acceptStickyElement(element: PsiElement): Boolean {
        val kind = classifyPython(element) ?: return false
        return isKindEnabledForSticky(kind)
    }

    override fun getElementInfo(element: PsiElement): String {
        val kind = classifyPython(element) ?: return ""
        return labelFor(element, kind)
    }
}

private val PYTHON_SIMPLE_NAME_KIND: Map<String, StickyKind> = mapOf(
    "PyClass" to StickyKind.CLASS_OR_FUNCTION,
    "PyFunction" to StickyKind.CLASS_OR_FUNCTION,
    "PyLambdaExpression" to StickyKind.CLASS_OR_FUNCTION,
    "PyIfStatement" to StickyKind.IF,
    "PyIfPartElif" to StickyKind.ELSEIF,
    "PyElsePart" to StickyKind.ELSE,
    "PyWhileStatement" to StickyKind.WHILE,
    "PyForStatement" to StickyKind.FOREACH,
    "PyTryExceptStatement" to StickyKind.TRY,
    "PyExceptPart" to StickyKind.CATCH,
    "PyFinallyPart" to StickyKind.FINALLY,
    "PyMatchStatement" to StickyKind.SWITCH,
    "PyCaseClause" to StickyKind.IF
)

private fun classifyPython(element: PsiElement): StickyKind? =
    classifyBySimpleName(element, PYTHON_SIMPLE_NAME_KIND)
