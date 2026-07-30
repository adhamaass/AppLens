package com.applens.processor

import com.applens.data.AppMetadata
import com.applens.data.ScreenInfo

/**
 * Generate a Markdown report summarizing the extraction.
 * Runs entirely on-device — no backend needed.
 */
object ReportGenerator {

    fun generate(
        appName: String,
        packageName: String,
        version: String,
        metadata: AppMetadata?,
        screens: List<ScreenInfo>,
        screenInfos: List<NavGraphBuilder.ScreenInfo>,
        navEdges: List<NavGraphBuilder.NavEdge>,
        componentTrees: List<ScreenTree>
    ): String {
        val sb = StringBuilder()

        sb.append("# AppLens Extraction Report\n\n")
        sb.append("**App:** $appName\n")
        sb.append("**Package:** $packageName\n")
        sb.append("**Version:** $version\n")
        sb.append("**Generated:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
        sb.append("---\n\n")

        // Summary stats
        sb.append("## Summary Statistics\n\n")
        sb.append("| Metric | Value |\n")
        sb.append("|--------|-------|\n")
        sb.append("| Screens Extracted | ${screenInfos.size} |\n")
        sb.append("| Navigation Edges | ${navEdges.size} |\n")
        sb.append("| Total UI Components | ${componentTrees.sumOf { it.nodeCount }} |\n")

        metadata?.let {
            sb.append("| Activities Declared | ${it.activities.size} |\n")
            sb.append("| Services Declared | ${it.services.size} |\n")
            sb.append("| Receivers Declared | ${it.receivers.size} |\n")
            sb.append("| Providers Declared | ${it.providers.size} |\n")
            sb.append("| Permissions Requested | ${it.permissions.size} |\n")
            sb.append("| Target SDK | ${it.sdkInfo.targetSdk} |\n")
            sb.append("| Min SDK | ${it.sdkInfo.minSdk} |\n")
        }

        val maxDepth = screenInfos.maxOfOrNull { it.depth } ?: 0
        sb.append("\n**Max Depth Reached:** $maxDepth\n\n")
        sb.append("---\n\n")

        // Screens detail
        sb.append("## Screens Extracted\n\n")
        sb.append("| # | Screen ID | Activity | Depth | Clickables | Patterns |\n")
        sb.append("|---|-----------|----------|-------|------------|----------|\n")
        screenInfos.forEachIndexed { i, screen ->
            val patterns = screen.patterns.joinToString(", ").ifEmpty { "None" }
            sb.append("| ${i + 1} | ${screen.id} | ${screen.activityName} | ${screen.depth} | ${screen.clickCount} | $patterns |\n")
        }
        sb.append("\n")

        // Navigation graph
        sb.append("---\n\n")
        sb.append("## Navigation Graph\n\n")
        if (navEdges.isEmpty()) {
            sb.append("No navigation transitions detected.\n\n")
        } else {
            sb.append("| From | To |\n")
            sb.append("|------|-----|\n")
            for (edge in navEdges) sb.append("| ${edge.from} | ${edge.to} |\n")
            sb.append("\n*See `flow.mmd` for a Mermaid visualization.*\n\n")
        }

        // Permissions
        metadata?.permissions?.takeIf { it.isNotEmpty() }?.let {
            sb.append("---\n\n## Permissions\n\n")
            sb.append("| Permission | Granted | Dangerous |\n")
            sb.append("|-----------|---------|----------|\n")
            for (perm in it) sb.append("| `${perm.name}` | ${if (perm.granted) "Yes" else "No"} | ${if (perm.dangerous) "Yes" else "No"} |\n")
            sb.append("\n")
        }

        // Activities
        metadata?.activities?.takeIf { it.isNotEmpty() }?.let {
            sb.append("---\n\n## Declared Activities\n\n")
            for (a in it) sb.append("- `$a`\n")
            sb.append("\n")
        }

        // Services
        metadata?.services?.takeIf { it.isNotEmpty() }?.let {
            sb.append("---\n\n## Declared Services\n\n")
            for (s in it) sb.append("- `$s`\n")
            sb.append("\n")
        }

        // Receivers
        metadata?.receivers?.takeIf { it.isNotEmpty() }?.let {
            sb.append("---\n\n## Broadcast Receivers\n\n")
            for (r in it) sb.append("- `$r`\n")
            sb.append("\n")
        }

        // Providers
        metadata?.providers?.takeIf { it.isNotEmpty() }?.let {
            sb.append("---\n\n## Content Providers\n\n")
            for (p in it) sb.append("- `$p`\n")
            sb.append("\n")
        }

        // Component breakdown per screen
        sb.append("---\n\n## Component Breakdown Per Screen\n\n")
        for (tree in componentTrees) {
            if (tree.root != null) {
                val counts = countComponentTypes(tree.root)
                sb.append("### ${tree.screenId}\n\n")
                sb.append("Total nodes: ${tree.nodeCount}\n\n")
                val top = counts.entries.sortedByDescending { it.value }.take(10)
                if (top.isNotEmpty()) {
                    sb.append("| Component | Count |\n|-----------|-------|\n")
                    for ((name, count) in top) sb.append("| $name | $count |\n")
                    sb.append("\n")
                }
            }
        }

        sb.append("---\n\n*Generated by AppLens on-device — no screenshots, no AI, no backend server.*\n")
        return sb.toString()
    }

    private fun countComponentTypes(node: ComponentNode): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        fun walk(n: ComponentNode) {
            val short = XmlProcessor.getClassShortName(n.className)
            counts[short] = (counts[short] ?: 0) + 1
            for (c in n.children) walk(c)
        }
        walk(node)
        return counts
    }
}
