package com.applens.processor

import com.applens.data.AppMetadata
import com.applens.data.ScreenInfo
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the final ZIP file entirely on-device.
 * Replaces the Node.js archiver logic.
 */
object ZipBuilder {

    /**
     * Process all extracted screens and metadata, then build the ZIP.
     * Returns the path to the saved ZIP file.
     */
    fun buildZip(
        outputDir: File,
        appName: String,
        packageName: String,
        version: String,
        metadata: AppMetadata,
        screens: List<ScreenInfo>
    ): File {
        // Process each screen
        val componentTrees = mutableListOf<ScreenTree>()
        val wireframes = mutableListOf<Pair<String, String>>() // (screenId, svg)
        val screenInfos = mutableListOf<NavGraphBuilder.ScreenInfo>()
        val navEdges = mutableListOf<NavGraphBuilder.NavEdge>()
        var prevActivity: String? = null

        for (screen in screens) {
            // Parse XML to component tree
            val tree = XmlProcessor.parseScreenXml(screen.xml, screen.id)
            componentTrees.add(tree)

            // Generate wireframe SVG
            val svg = WireframeGenerator.generateSVG(tree)
            wireframes.add(Pair(screen.id, svg))

            // Detect patterns
            val patternsResult = PatternDetector.detect(tree)
            screenInfos.add(NavGraphBuilder.ScreenInfo(
                id = screen.id,
                activityName = screen.activityName,
                patterns = patternsResult.patterns,
                depth = screen.depth,
                clickCount = screen.clickCount
            ))

            // Build nav edges
            if (prevActivity != null && prevActivity != screen.activityName) {
                navEdges.add(NavGraphBuilder.NavEdge(prevActivity!!, screen.activityName))
            }
            prevActivity = screen.activityName
        }

        // Build navigation graph
        val navGraph = NavGraphBuilder.buildGraph(screenInfos, navEdges)
        val mermaid = NavGraphBuilder.exportMermaid(navGraph)

        // Generate report
        val report = ReportGenerator.generate(
            appName, packageName, version, metadata,
            screens, screenInfos, navEdges, componentTrees
        )

        // Build ZIP
        val zipFile = File(outputDir, "${packageName}_output.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // screens/
            for (screen in screens) {
                zos.putNextEntry(ZipEntry("screens/${screen.id}.xml"))
                zos.write(screen.xml.toByteArray())
                zos.closeEntry()
            }

            // wireframes/
            for ((screenId, svg) in wireframes) {
                zos.putNextEntry(ZipEntry("wireframes/$screenId.svg"))
                zos.write(svg.toByteArray())
                zos.closeEntry()
            }

            // components/
            for (tree in componentTrees) {
                zos.putNextEntry(ZipEntry("components/${tree.screenId}.json"))
                zos.write(treeToJson(tree).toByteArray())
                zos.closeEntry()
            }

            // manifest/
            addJsonEntry(zos, "manifest/permissions.json", metadata.permissions)
            addJsonEntry(zos, "manifest/activities.json", metadata.activities)
            addJsonEntry(zos, "manifest/services.json", metadata.services)
            addJsonEntry(zos, "manifest/receivers.json", metadata.receivers)
            addJsonEntry(zos, "manifest/providers.json", metadata.providers)

            zos.putNextEntry(ZipEntry("manifest/app_info.json"))
            zos.write(appInfoToJson(metadata).toByteArray())
            zos.closeEntry()

            // flow.mmd
            zos.putNextEntry(ZipEntry("flow.mmd"))
            zos.write(mermaid.toByteArray())
            zos.closeEntry()

            // report.md
            zos.putNextEntry(ZipEntry("report.md"))
            zos.write(report.toByteArray())
            zos.closeEntry()
        }

        return zipFile
    }

    private fun addJsonEntry(zos: ZipOutputStream, path: String, data: Any) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(data.toString().toByteArray())
        zos.closeEntry()
    }

    private fun treeToJson(tree: ScreenTree): String {
        if (tree.root == null) return """{"screenId":"${tree.screenId}","root":null,"nodeCount":0}"""
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"screenId\": \"${tree.screenId}\",\n")
        sb.append("  \"nodeCount\": ${tree.nodeCount},\n")
        sb.append("  \"root\": ")
        nodeToJson(tree.root, sb, 1)
        sb.append("\n}")
        return sb.toString()
    }

    private fun nodeToJson(node: ComponentNode, sb: StringBuilder, indent: Int) {
        val pad = "  ".repeat(indent)
        sb.append("{\n")
        sb.append("$pad  \"class\": \"${escapeJson(node.className)}\",\n")
        sb.append("$pad  \"resource-id\": \"${escapeJson(node.resourceId)}\",\n")
        sb.append("$pad  \"text\": \"${escapeJson(node.text)}\",\n")
        sb.append("$pad  \"content-desc\": \"${escapeJson(node.contentDesc)}\",\n")
        sb.append("$pad  \"bounds\": \"${node.bounds}\",\n")
        sb.append("$pad  \"clickable\": ${node.clickable},\n")
        sb.append("$pad  \"scrollable\": ${node.scrollable},\n")
        sb.append("$pad  \"focusable\": ${node.focusable},\n")
        sb.append("$pad  \"enabled\": ${node.enabled},\n")
        sb.append("$pad  \"depth\": ${node.depth},\n")
        sb.append("$pad  \"children\": [")
        if (node.children.isEmpty()) {
            sb.append("]\n")
        } else {
            sb.append("\n")
            node.children.forEachIndexed { i, child ->
                nodeToJson(child, sb, indent + 2)
                if (i < node.children.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("$pad  ]\n")
        }
        sb.append("$pad}")
    }

    private fun appInfoToJson(metadata: AppMetadata): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"appName\": \"${escapeJson(metadata.appName)}\",\n")
        sb.append("  \"packageName\": \"${escapeJson(metadata.packageName)}\",\n")
        sb.append("  \"version\": \"${escapeJson(metadata.version)}\",\n")
        sb.append("  \"apkPath\": \"${escapeJson(metadata.apkPath)}\",\n")
        sb.append("  \"installDate\": ${metadata.installDate},\n")
        sb.append("  \"lastUpdateDate\": ${metadata.lastUpdateDate},\n")
        sb.append("  \"sdkInfo\": {\"minSdk\": ${metadata.sdkInfo.minSdk}, \"targetSdk\": ${metadata.sdkInfo.targetSdk}, \"compileSdk\": ${metadata.sdkInfo.compileSdk}},\n")
        sb.append("  \"permissions\": ${permissionsToJson(metadata.permissions)},\n")
        sb.append("  \"activities\": ${listToJson(metadata.activities)},\n")
        sb.append("  \"services\": ${listToJson(metadata.services)},\n")
        sb.append("  \"receivers\": ${listToJson(metadata.receivers)},\n")
        sb.append("  \"providers\": ${listToJson(metadata.providers)}\n")
        sb.append("}")
        return sb.toString()
    }

    private fun permissionsToJson(perms: List<com.applens.data.PermissionInfo>): String {
        if (perms.isEmpty()) return "[]"
        val sb = StringBuilder("[\n")
        perms.forEachIndexed { i, p ->
            sb.append("  {\"name\": \"${escapeJson(p.name)}\", \"granted\": ${p.granted}, \"dangerous\": ${p.dangerous}}")
            if (i < perms.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun listToJson(items: List<String>): String {
        if (items.isEmpty()) return "[]"
        return items.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") {
            "  \"${escapeJson(it)}\""
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }
}
