package com.racha.ai.core.eve

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // يتم تحديث نافذة النظام النشطة تلقائياً
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * استخراج اسم التطبيق والنصوص الظاهرة على الشاشة
     */
    fun captureCurrentScreen(): ScreenSnapshot? {
        val rootNode = rootInActiveWindow ?: return null
        val pkgName = rootNode.packageName?.toString() ?: ""

        val appName = try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkgName
        }

        val extractedTexts = mutableListOf<String>()
        extractTextFromNode(rootNode, extractedTexts)

        val fullText = extractedTexts
            .filter { it.isNotBlank() && it.length > 2 }
            .distinct()
            .joinToString("\n")
            .take(1500) // حصر النص لتسريع استجابة الذكاء الاصطناعي

        return ScreenSnapshot(
            packageName = pkgName,
            appName = appName,
            visibleText = fullText
        )
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return

        node.text?.toString()?.let { list.add(it) }
        node.contentDescription?.toString()?.let { list.add(it) }

        for (i in 0 until node.childCount) {
            extractTextFromNode(node.getChild(i), list)
        }
    }

    companion object {
        var instance: EveAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}

data class ScreenSnapshot(
    val packageName: String,
    val appName: String,
    val visibleText: String
)
