package com.github.erjotek.stickyprojectfolder.editor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class PhpStickyBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        val php = Language.findLanguageByID("PHP") ?: return emptyArray()
        return arrayOf(php)
    }

    override fun acceptElement(element: PsiElement): Boolean {
        return classifyPhp(element) != null
    }

    @Suppress("UnstableApiUsage")
    override fun acceptStickyElement(element: PsiElement): Boolean {
        val kind = classifyPhp(element) ?: return false
        if (kind == StickyKind.NAMESPACE) return false
        return isKindEnabledForSticky(kind)
    }

    override fun getElementInfo(element: PsiElement): String {
        val kind = classifyPhp(element) ?: return ""
        return labelFor(element, kind)
    }
}

private val PHP_CLASS_KIND: Map<String, StickyKind> = mapOf(
    "com.jetbrains.php.lang.psi.elements.PhpClass" to StickyKind.CLASS_OR_FUNCTION,
    "com.jetbrains.php.lang.psi.elements.impl.PhpClassImpl" to StickyKind.CLASS_OR_FUNCTION,
    "com.jetbrains.php.lang.psi.elements.Method" to StickyKind.CLASS_OR_FUNCTION,
    "com.jetbrains.php.lang.psi.elements.impl.MethodImpl" to StickyKind.CLASS_OR_FUNCTION,
    "com.jetbrains.php.lang.psi.elements.Function" to StickyKind.CLASS_OR_FUNCTION,
    "com.jetbrains.php.lang.psi.elements.impl.FunctionImpl" to StickyKind.CLASS_OR_FUNCTION,
    "com.jetbrains.php.lang.psi.elements.PhpNamespace" to StickyKind.NAMESPACE,
    "com.jetbrains.php.lang.psi.elements.impl.PhpNamespaceImpl" to StickyKind.NAMESPACE
)

private val PHP_SIMPLE_NAME_KIND: Map<String, StickyKind> = mapOf(
    "If" to StickyKind.IF,
    "ElseIf" to StickyKind.ELSEIF,
    "Else" to StickyKind.ELSE,
    "For" to StickyKind.FOR,
    "ForeachStatement" to StickyKind.FOREACH,
    "Foreach" to StickyKind.FOREACH,
    "While" to StickyKind.WHILE,
    "DoWhile" to StickyKind.DO,
    "Switch" to StickyKind.SWITCH,
    "Try" to StickyKind.TRY,
    "Catch" to StickyKind.CATCH,
    "Finally" to StickyKind.FINALLY,
    "ArrayCreationExpression" to StickyKind.ARRAY
)

private fun classifyPhp(element: PsiElement): StickyKind? =
    classifyByClassNameOrSimpleName(element, PHP_CLASS_KIND, PHP_SIMPLE_NAME_KIND)
