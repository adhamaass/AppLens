const express = require('express');
const cors = require('cors');
const { XMLParser } = require('fast-xml-parser');
const archiver = require('archiver');
const { parseScreenXml, buildComponentTree } = require('./xmlParser');
const { generateWireframeSVG } = require('./wireframeGenerator');
const { detectPatterns } = require('./patternDetector');
const { buildNavGraph, exportMermaid } = require('./navGraphBuilder');
const { generateReport } = require('./reportGenerator');

const app = express();
app.use(cors());
app.use(express.json({ limit: '500mb' }));
app.use(express.urlencoded({ extended: true, limit: '500mb' }));

const PORT = process.env.PORT || 3000;

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Main endpoint: POST /analyze
app.post('/analyze', async (req, res) => {
  try {
    const { appName, packageName, version, metadata, screens } = req.body;

    if (!packageName || !screens || !Array.isArray(screens)) {
      return res.status(400).json({ error: 'Missing required fields: packageName, screens[]' });
    }

    console.log(`[AppLens] Processing ${screens.length} screens for ${appName} (${packageName})`);

    const components = [];
    const wireframes = [];
    const screenNames = [];
    const navEdges = [];
    let prevActivity = null;

    for (let i = 0; i < screens.length; i++) {
      const screen = screens[i];
      console.log(`  Processing ${screen.id}: ${screen.activityName}`);

      // Parse XML to component tree
      const tree = parseScreenXml(screen.xml, screen.id);
      components.push({ screenId: screen.id, tree });

      // Generate wireframe SVG
      const svg = generateWireframeSVG(tree, screen.id);
      wireframes.push({ screenId: screen.id, svg });

      // Detect patterns — patternsResult.patterns is the array
      const patternsResult = detectPatterns(tree);
      screenNames.push({
        id: screen.id,
        activityName: screen.activityName,
        patterns: patternsResult.patterns,
        depth: screen.depth,
        clickCount: screen.clickCount
      });

      // Build nav edges
      if (prevActivity && prevActivity !== screen.activityName) {
        navEdges.push({ from: prevActivity, to: screen.activityName });
      }
      prevActivity = screen.activityName;
    }

    // Build navigation graph
    const navGraph = buildNavGraph(screenNames, navEdges);
    const mermaid = exportMermaid(navGraph);

    // Generate report
    const report = generateReport({
      appName, packageName, version, metadata,
      screenNames, navEdges, components
    });

    // Build and send ZIP
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', `attachment; filename="${packageName}_output.zip"`);

    const archive = archiver('zip', { zlib: { level: 9 } });
    archive.on('error', (err) => {
      console.error('[AppLens] Archive error:', err);
      res.status(500).json({ error: 'Failed to create ZIP' });
    });

    archive.pipe(res);

    // screens/
    for (const screen of screens) {
      archive.append(screen.xml, { name: `screens/${screen.id}.xml` });
    }

    // wireframes/
    for (const wf of wireframes) {
      archive.append(wf.svg, { name: `wireframes/${wf.screenId}.svg` });
    }

    // components/
    for (const comp of components) {
      archive.append(JSON.stringify(comp.tree, null, 2), { name: `components/${comp.screenId}.json` });
    }

    // manifest/
    archive.append(JSON.stringify(metadata.permissions || [], null, 2), { name: 'manifest/permissions.json' });
    archive.append(JSON.stringify(metadata.activities || [], null, 2), { name: 'manifest/activities.json' });
    archive.append(JSON.stringify(metadata.services || [], null, 2), { name: 'manifest/services.json' });
    archive.append(JSON.stringify({
      appName: metadata.appName,
      packageName: metadata.packageName,
      version: metadata.version,
      apkPath: metadata.apkPath,
      installDate: metadata.installDate,
      lastUpdateDate: metadata.lastUpdateDate,
      sdkInfo: metadata.sdkInfo,
      permissions: metadata.permissions,
      activities: metadata.activities,
      services: metadata.services,
      receivers: metadata.receivers,
      providers: metadata.providers
    }, null, 2), { name: 'manifest/app_info.json' });
    archive.append(JSON.stringify(metadata.receivers || [], null, 2), { name: 'manifest/receivers.json' });
    archive.append(JSON.stringify(metadata.providers || [], null, 2), { name: 'manifest/providers.json' });

    // flow.mmd
    archive.append(mermaid, { name: 'flow.mmd' });

    // report.md
    archive.append(report, { name: 'report.md' });

    await archive.finalize();
    console.log(`[AppLens] ZIP sent successfully`);

  } catch (error) {
    console.error('[AppLens] Error:', error);
    res.status(500).json({ error: error.message });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`[AppLens] Backend running on http://0.0.0.0:${PORT}`);
  console.log(`[AppLens] Waiting for app extraction data...`);
});
