package com.github.erjotek.stickyprojectfolder.editor

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class JsStickyBreadcrumbsProvider : BreadcrumbsProvider {

    override fun getLanguages(): Array<Language> {
        val ids = listOf("JavaScript", "TypeScript", "ECMAScript 6", "TypeScript JSX", "JSX Harmony")
        return ids.mapNotNull { Language.findLanguageByID(it) }.toTypedArray()
    }

    override fun acceptElement(element: PsiElement): Boolean {
        return classifyJs(element) != null
    }

    @Suppress("UnstableApiUsage")
    override fun acceptStickyElement(element: PsiElement): Boolean {
        val kind = classifyJs(element) ?: return false
        return isKindEnabledForSticky(kind)
    }

    override fun getElementInfo(element: PsiElement): String {
        val kind = classifyJs(element) ?: return ""
        return labelFor(element, kind)
    }
}

private val JS_SIMPLE_NAME_KIND: Map<String, StickyKind> = mapOf(
    "JSFunction" to StickyKind.CLASS_OR_FUNCTION,
    "JSFunctionDeclaration" to StickyKind.CLASS_OR_FUNCTION,
    "JSFunctionExpression" to StickyKind.CLASS_OR_FUNCTION,
    "JSClass" to StickyKind.CLASS_OR_FUNCTION,
    "TypeScriptFunction" to StickyKind.CLASS_OR_FUNCTION,
    "TypeScriptClass" to StickyKind.CLASS_OR_FUNCTION,
    "JSIfStatement" to StickyKind.IF,
    "JSForStatement" to StickyKind.FOR,
    "JSForInStatement" to StickyKind.FOREACH,
    "JSForOfStatement" to StickyKind.FOREACH,
    "JSWhileStatement" to StickyKind.WHILE,
    "JSDoWhileStatement" to StickyKind.DO,
    "JSSwitchStatement" to StickyKind.SWITCH,
    "JSTryStatement" to StickyKind.TRY,
    "JSCatchBlock" to StickyKind.CATCH,
    "JSObjectLiteralExpression" to StickyKind.ARRAY,
    "JSArrayLiteralExpression" to StickyKind.ARRAY
)

private fun classifyJs(element: PsiElement): StickyKind? =
    classifyBySimpleName(element, JS_SIMPLE_NAME_KIND)
