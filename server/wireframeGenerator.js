/**
 * Generate a wireframe SVG from a parsed component tree.
 * Draws rectangles per node, labeled with class shortname, sized proportionally.
 */

const COLORS = {
  background: '#f5f5f5',
  border: '#333333',
  fill: '#ffffff',
  text: '#333333',
  clickable: '#4a90d9',
  scrollable: '#e67e22',
  textInput: '#2ecc71',
  button: '#3498db',
  image: '#9b59b6',
  list: '#f39c12',
  text: '#34495e',
  header: '#2c3e50',
  container: '#ecf0f1'
};

function generateWireframeSVG(tree, screenId) {
  if (!tree || !tree.root) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="2400"><text x="20" y="30" fill="#999">No data for ${screenId}</text></svg>`;
  }

  // Find screen dimensions from the root bounds
  const rootBounds = tree.root.boundsParsed;
  const screenW = rootBounds ? rootBounds.width : 1080;
  const screenH = rootBounds ? rootBounds.height : 2400;

  // Scale to a reasonable SVG size
  const maxW = 540;
  const scale = maxW / screenW;
  const svgW = Math.round(screenW * scale);
  const svgH = Math.round(screenH * scale);

  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${svgW}" height="${svgH}" viewBox="0 0 ${svgW} ${svgH}">`;
  svg += `<rect width="${svgW}" height="${svgH}" fill="${COLORS.background}" stroke="${COLORS.border}" stroke-width="1"/>`;

  // Draw nodes
  function drawNode(node) {
    if (!node || !node.boundsParsed) return;

    const b = node.boundsParsed;
    const x = Math.round(b.x * scale);
    const y = Math.round(b.y * scale);
    const w = Math.round(b.width * scale);
    const h = Math.round(b.height * scale);

    // Skip tiny or invisible elements
    if (w < 2 || h < 2) return;

    const shortClass = getClassShortName(node.class);
    const fill = getNodeColor(node);
    const stroke = node.clickable ? COLORS.clickable : COLORS.border;
    const strokeWidth = node.clickable ? 2 : 1;

    // Draw rectangle
    svg += `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}" fill-opacity="0.3" stroke="${stroke}" stroke-width="${strokeWidth}" rx="2"/>`;

    // Label if element is big enough
    if (w > 40 && h > 16) {
      const label = shortClass;
      const fontSize = Math.min(12, Math.max(8, h / 3));

      // Label background
      if (node.text || shortClass) {
        svg += `<rect x="${x + 2}" y="${y + 2}" width="${Math.min(w - 4, label.length * fontSize * 0.6 + 8)}" height="${fontSize + 4}" fill="#ffffff" fill-opacity="0.85" rx="1"/>`;
        svg += `<text x="${x + 6}" y="${y + fontSize + 3}" font-family="monospace" font-size="${fontSize}" fill="${COLORS.text}">${escapeXml(label)}</text>`;
      }

      // Show text content if present and element is text-like
      if (node.text && w > 60 && h > 20) {
        const displayText = truncate(node.text, Math.floor(w / (fontSize * 0.55)));
        svg += `<text x="${x + 6}" y="${y + h / 2 + 4}" font-family="sans-serif" font-size="${fontSize}" fill="${COLORS.text}">${escapeXml(displayText)}</text>`;
      }
    }

    // Recurse into children
    if (node.children) {
      for (const child of node.children) {
        drawNode(child);
      }
    }
  }

  drawNode(tree.root);

  // Title
  svg += `<text x="10" y="${svgH - 10}" font-family="monospace" font-size="10" fill="#999">${screenId} | ${tree.nodeCount} nodes | ${svgW}x${svgH}</text>`;
  svg += `</svg>`;

  return svg;
}

/**
 * Get a short class name from a full Android class path.
 * e.g. "android.widget.TextView" → "TextView"
 */
function getClassShortName(className) {
  if (!className) return 'View';
  const parts = className.split('.');
  return parts[parts.length - 1] || className;
}

/**
 * Assign a color based on node type.
 */
function getNodeColor(node) {
  const cls = (node.class || '').toLowerCase();

  if (cls.includes('button')) return COLORS.button;
  if (cls.includes('edittext') || cls.includes('textinput')) return COLORS.textInput;
  if (cls.includes('imageview') || cls.includes('image')) return COLORS.image;
  if (cls.includes('recyclerview') || cls.includes('listview') || cls.includes('scrollview')) return COLORS.list;
  if (cls.includes('textview') || cls.includes('text')) return COLORS.text;
  if (cls.includes('toolbar') || cls.includes('appbar') || cls.includes('actionbar')) return COLORS.header;
  if (cls.includes('layout') || cls.includes('container') || cls.includes('group')) return COLORS.container;

  if (node.clickable) return COLORS.clickable;
  if (node.scrollable) return COLORS.scrollable;
  return COLORS.fill;
}

function escapeXml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function truncate(str, maxLen) {
  if (str.length <= maxLen) return str;
  return str.substring(0, maxLen - 3) + '...';
}

module.exports = { generateWireframeSVG };
