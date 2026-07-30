/**
 * Detect component patterns in a parsed screen tree.
 * Tags screens based on the UI components present.
 */

function detectPatterns(tree) {
  if (!tree || !tree.root) return { patterns: [], summary: 'Empty screen' };

  const patterns = [];
  const componentCounts = {};

  function walk(node) {
    if (!node) return;

    const cls = (node.class || '').toLowerCase();

    // Count component types
    const shortName = getShortName(node.class);
    componentCounts[shortName] = (componentCounts[shortName] || 0) + 1;

    // Pattern detection
    if (cls.includes('recyclerview') || cls.includes('listview')) {
      if (!patterns.includes('List')) patterns.push('List');
    }
    if (cls.includes('edittext') || cls.includes('textinputedittext') || cls.includes('textinput')) {
      if (!patterns.includes('Input Form')) patterns.push('Input Form');
    }
    if (cls.includes('bottomnavigationview') || cls.includes('bottomnavigation')) {
      if (!patterns.includes('Bottom Nav')) patterns.push('Bottom Nav');
    }
    if (cls.includes('navigationview') || cls.includes('drawerlayout')) {
      if (!patterns.includes('Navigation Drawer')) patterns.push('Navigation Drawer');
    }
    if (cls.includes('webview')) {
      if (!patterns.includes('WebView')) patterns.push('WebView');
    }
    if (cls.includes('viewpager') || cls.includes('viewpager2')) {
      if (!patterns.includes('ViewPager')) patterns.push('ViewPager');
    }
    if (cls.includes('tablayout') || cls.includes('tabhost')) {
      if (!patterns.includes('Tab Layout')) patterns.push('Tab Layout');
    }
    if (cls.includes('toolbar') || cls.includes('appbar') || cls.includes('actionbar')) {
      if (!patterns.includes('Toolbar/AppBar')) patterns.push('Toolbar/AppBar');
    }
    if (cls.includes('floatingactionbutton') || cls.includes('fab')) {
      if (!patterns.includes('FAB')) patterns.push('FAB');
    }
    if (cls.includes('imageview') || cls.includes('image')) {
      if (!patterns.includes('Images')) patterns.push('Images');
    }
    if (cls.includes('checkbox')) {
      if (!patterns.includes('Checkbox')) patterns.push('Checkbox');
    }
    if (cls.includes('radiobutton')) {
      if (!patterns.includes('Radio Button')) patterns.push('Radio Button');
    }
    if (cls.includes('switch') || cls.includes('togglebutton')) {
      if (!patterns.includes('Switch/Toggle')) patterns.push('Switch/Toggle');
    }
    if (cls.includes('spinner') || cls.includes('dropdown')) {
      if (!patterns.includes('Dropdown/Spinner')) patterns.push('Dropdown/Spinner');
    }
    if (cls.includes('searchview') || cls.includes('searchbar')) {
      if (!patterns.includes('Search Bar')) patterns.push('Search Bar');
    }
    if (cls.includes('datepicker') || cls.includes('timepicker') || cls.includes('calendar')) {
      if (!patterns.includes('Date/Time Picker')) patterns.push('Date/Time Picker');
    }
    if (cls.includes('video') || cls.includes('exoplayer') || cls.includes('mediaplayer')) {
      if (!patterns.includes('Media Player')) patterns.push('Media Player');
    }
    if (cls.includes('recyclerview') && componentCounts['RecyclerView'] > 3) {
      if (!patterns.includes('Multiple Lists')) patterns.push('Multiple Lists');
    }
    if (cls.includes('dialog') || cls.includes('alertdialog') || cls.includes('modal')) {
      if (!patterns.includes('Dialog')) patterns.push('Dialog');
    }
    if (cls.includes('progressbar') || cls.includes('loading')) {
      if (!patterns.includes('Progress/Loading')) patterns.push('Progress/Loading');
    }

    // Recurse
    if (node.children) {
      for (const child of node.children) walk(child);
    }
  }

  walk(tree.root);

  // Build summary
  const topComponents = Object.entries(componentCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([name, count]) => `${name}(${count})`)
    .join(', ');

  return {
    patterns,
    componentCounts,
    summary: topComponents || 'No components',
    totalNodes: tree.nodeCount
  };
}

function getShortName(className) {
  if (!className) return 'View';
  const parts = className.split('.');
  return parts[parts.length - 1] || className;
}

module.exports = { detectPatterns };
