const { XMLParser } = require('fast-xml-parser');

const parser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '',
  parseAttributeValue: true,
  trimValues: true,
  parseTagValue: false,
});

/**
 * Parse a UIAutomator XML dump and build a structured component tree.
 * Preserves: class, resource-id, text, content-desc, bounds, clickable,
 * scrollable, focusable, enabled, depth.
 */
function parseScreenXml(xml, screenId) {
  try {
    const parsed = parser.parse(xml);
    const hierarchy = parsed.hierarchy;
    if (!hierarchy || !hierarchy.node) {
      return { screenId, root: null, nodeCount: 0 };
    }
    const tree = buildComponentTree(hierarchy.node, 0);
    const count = countNodes(tree);
    return { screenId, root: tree, nodeCount: count };
  } catch (e) {
    console.error(`Error parsing ${screenId}:`, e.message);
    return { screenId, root: null, nodeCount: 0, error: e.message };
  }
}

/**
 * Recursively build a component tree from the XML node.
 */
function buildComponentTree(node, depth) {
  if (!node || typeof node !== 'object') return null;

  const attrs = node['@_class'] !== undefined ? node : (node.node ? null : node);

  // Handle both attribute-prefixed and non-prefixed versions
  const getAttr = (key) => {
    return node[key] !== undefined ? node[key] :
           node['@_' + key] !== undefined ? node['@_' + key] :
           null;
  };

  // Get bounds and parse to numbers
  const boundsStr = getAttr('bounds') || '';
  const bounds = parseBounds(boundsStr);

  const result = {
    class: getAttr('class') || '',
    'resource-id': getAttr('resource-id') || '',
    text: getAttr('text') || '',
    'content-desc': getAttr('content-desc') || '',
    bounds: boundsStr,
    boundsParsed: bounds, // { x, y, width, height }
    clickable: getAttr('clickable') === true || getAttr('clickable') === 'true',
    scrollable: getAttr('scrollable') === true || getAttr('scrollable') === 'true',
    focusable: getAttr('focusable') === true || getAttr('focusable') === 'true',
    enabled: getAttr('enabled') !== false && getAttr('enabled') !== 'false',
    'checkable': getAttr('checkable') === true || getAttr('checkable') === 'true',
    'checked': getAttr('checked') === true || getAttr('checked') === 'true',
    'long-clickable': getAttr('long-clickable') === true || getAttr('long-clickable') === 'true',
    'focusable-in-touch-mode': getAttr('focusable-in-touch-mode') === true || getAttr('focusable-in-touch-mode') === 'true',
    'package': getAttr('package') || '',
    depth: depth,
    children: []
  };

  // Process child nodes
  const childNodes = node.node;
  if (childNodes) {
    const childArray = Array.isArray(childNodes) ? childNodes : [childNodes];
    for (const child of childArray) {
      const childTree = buildComponentTree(child, depth + 1);
      if (childTree) result.children.push(childTree);
    }
  }

  return result;
}

/**
 * Parse a bounds string like "[0,100][540,300]" into structured data.
 */
function parseBounds(boundsStr) {
  if (!boundsStr) return null;
  const match = boundsStr.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/);
  if (!match) return null;
  const [, x1, y1, x2, y2] = match.map(Number);
  return {
    x: x1, y: y1,
    width: x2 - x1,
    height: y2 - y1,
    x2, y2
  };
}

/**
 * Count all nodes in a tree.
 */
function countNodes(node) {
  if (!node) return 0;
  let count = 1;
  if (node.children) {
    for (const child of node.children) {
      count += countNodes(child);
    }
  }
  return count;
}

module.exports = { parseScreenXml, buildComponentTree, countNodes, parseBounds };
