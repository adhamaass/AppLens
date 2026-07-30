/**
 * Build a navigation graph from screen data and export as Mermaid.
 * Each node = a screen (activityName), each edge = a screen transition.
 */

/**
 * Build the navigation graph.
 * @param {Array} screens - Array of { id, activityName, patterns, depth, clickCount }
 * @param {Array} edges - Array of { from, to } activity transitions
 */
function buildNavGraph(screens, edges) {
  const nodes = new Map();
  const edgeSet = new Set();

  // Collect unique activity names as nodes
  for (const screen of screens) {
    if (!nodes.has(screen.activityName)) {
      nodes.set(screen.activityName, {
        id: screen.id,
        label: screen.activityName,
        depth: screen.depth,
        patterns: screen.patterns || []
      });
    }
  }

  // Deduplicate edges
  for (const edge of edges) {
    const key = `${edge.from}-->${edge.to}`;
    if (edge.from !== edge.to) {
      edgeSet.add(key);
    }
  }

  return {
    nodes: Array.from(nodes.values()),
    edges: Array.from(edgeSet).map(key => {
      const [from, to] = key.split('-->');
      return { from, to };
    })
  };
}

/**
 * Export the navigation graph as a Mermaid .mmd file.
 */
function exportMermaid(graph) {
  let mermaid = 'graph TD\n';

  // Define nodes with safe IDs
  const safeId = (name) => name.replace(/[^a-zA-Z0-9]/g, '_');
  const idMap = new Map();

  graph.nodes.forEach((node, i) => {
    const safe = safeId(node.label) || `Node${i}`;
    idMap.set(node.label, safe);
    mermaid += `  ${safe}["${node.label}"]\n`;
  });

  mermaid += '\n';

  // Define edges
  for (const edge of graph.edges) {
    const fromId = idMap.get(edge.from) || safeId(edge.from);
    const toId = idMap.get(edge.to) || safeId(edge.to);
    mermaid += `  ${fromId} --> ${toId}\n`;
  }

  // Add edge count
  mermaid += `\n%% ${graph.nodes.length} screens, ${graph.edges.length} transitions\n`;

  return mermaid;
}

module.exports = { buildNavGraph, exportMermaid };
