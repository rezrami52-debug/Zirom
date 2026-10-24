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
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // تجاهل نوافذ النظام وفقاعة Racha للحفاظ على تركيز التطبيق الحقيقي
        if (pkg != packageName && pkg != "com.android.systemui") {
            lastTargetPackage = pkg
            updateCurrentScreenSnapshot()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    private fun updateCurrentScreenSnapshot() {
        val rootNode = rootInActiveWindow ?: return
        val pkgName = rootNode.packageName?.toString() ?: lastTargetPackage

        if (pkgName == packageName || pkgName == "com.android.systemui") return

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
            .take(2000)

        if (fullText.isNotBlank()) {
            latestSnapshot = ScreenSnapshot(
                packageName = pkgName,
                appName = appName,
                visibleText = fullText
            )
        }
    }

    fun getLatestScreenSnapshot(): ScreenSnapshot? {
        updateCurrentScreenSnapshot()
        return latestSnapshot
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

        private var lastTargetPackage: String = ""
        private var latestSnapshot: ScreenSnapshot? = null

        fun isRunning(): Boolean = instance != null
    }
}

data class ScreenSnapshot(
    val packageName: String,
    val appName: String,
    val visibleText: String
)
