package com.applens.processor

/**
 * Build a navigation graph from screen data and export as Mermaid.
 * Each node = a screen (activityName), each edge = a screen transition.
 */
object NavGraphBuilder {

    data class NavNode(val id: String, val label: String, val depth: Int, val patterns: List<String>)
    data class NavEdge(val from: String, val to: String)
    data class NavGraph(val nodes: List<NavNode>, val edges: List<NavEdge>)

    data class ScreenInfo(
        val id: String,
        val activityName: String,
        val patterns: List<String>,
        val depth: Int,
        val clickCount: Int
    )

    fun buildGraph(screens: List<ScreenInfo>, edges: List<NavEdge>): NavGraph {
        val nodeMap = linkedMapOf<String, NavNode>()
        for (screen in screens) {
            if (screen.activityName !in nodeMap) {
                nodeMap[screen.activityName] = NavNode(screen.id, screen.activityName, screen.depth, screen.patterns)
            }
        }

        val edgeSet = linkedSetOf<String>()
        val dedupedEdges = mutableListOf<NavEdge>()
        for (edge in edges) {
            if (edge.from != edge.to) {
                val key = "${edge.from}-->${edge.to}"
                if (key !in edgeSet) {
                    edgeSet.add(key)
                    dedupedEdges.add(edge)
                }
            }
        }

        return NavGraph(nodeMap.values.toList(), dedupedEdges)
    }

    fun exportMermaid(graph: NavGraph): String {
        val sb = StringBuilder()
        sb.append("graph TD\n")

        val safeId = { name: String -> name.replace(Regex("[^a-zA-Z0-9]"), "_") }
        val idMap = mutableMapOf<String, String>()

        graph.nodes.forEachIndexed { i, node ->
            val safe = safeId(node.label).ifEmpty { "Node$i" }
            idMap[node.label] = safe
            sb.append("  $safe[\"${node.label}\"]\n")
        }

        sb.append("\n")

        for (edge in graph.edges) {
            val fromId = idMap[edge.from] ?: safeId(edge.from)
            val toId = idMap[edge.to] ?: safeId(edge.to)
            sb.append("  $fromId --> $toId\n")
        }

        sb.append("\n%% ${graph.nodes.size} screens, ${graph.edges.size} transitions\n")
        return sb.toString()
    }
}
