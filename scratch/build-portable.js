const fs = require('fs');
const path = require('path');
const vm = require('vm');

async function main() {
  console.log("1. Loading Babel Standalone...");
  let babelCode;
  const cachedBabel = path.join(__dirname, 'babel.min.js');
  if (fs.existsSync(cachedBabel)) {
    babelCode = fs.readFileSync(cachedBabel, 'utf8');
  } else {
    const res = await fetch('https://unpkg.com/@babel/standalone/babel.min.js');
    babelCode = await res.text();
    fs.writeFileSync(cachedBabel, babelCode, 'utf8');
  }

  const sandbox = { window: {}, console };
  vm.createContext(sandbox);
  vm.runInContext(babelCode, sandbox);
  const Babel = sandbox.Babel || sandbox.window.Babel;
  console.log("Babel ready!");

  const rootDir = path.resolve(__dirname, '..');
  const jsDir = path.join(rootDir, 'js');

  function getAllJsFiles(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    for (const file of list) {
      const fullPath = path.join(dir, file);
      const stat = fs.statSync(fullPath);
      if (stat.isDirectory()) {
        results = results.concat(getAllJsFiles(fullPath));
      } else if (file.endsWith('.js')) {
        results.push(fullPath);
      }
    }
    return results;
  }

  const allJsFiles = getAllJsFiles(jsDir);
  console.log(`\n2. Transpiling ${allJsFiles.length} JS files to pure browser JavaScript...`);

  for (const jsFile of allJsFiles) {
    const relPath = path.relative(rootDir, jsFile);
    const code = fs.readFileSync(jsFile, 'utf8');
    try {
      const transformed = Babel.transform(code, {
        presets: [['react', { runtime: 'classic' }]]
      }).code;
      fs.writeFileSync(jsFile, transformed, 'utf8');
      console.log(`  ✓ Transpiled: ${relPath}`);
    } catch (err) {
      console.error(`  ✗ Error in ${relPath}:`, err.message);
    }
  }

  console.log("\n3. Updating HTML files to use standard native <script> tags...");
  const htmlFiles = [
    'overview.html',
    'cashflow.html',
    'patrimoine.html',
    'retraite.html',
    'impots.html',
    'settings.html',
    'import.html',
    'pending.html',
    'pointage.html',
    'analyse.html'
  ];

  for (const htmlFile of htmlFiles) {
    const fullPath = path.join(rootDir, htmlFile);
    if (!fs.existsSync(fullPath)) continue;

    let html = fs.readFileSync(fullPath, 'utf8');

    // 1. Remove @babel/standalone CDN script tag
    html = html.replace(/<script\s+src="https:\/\/unpkg\.com\/@babel\/standalone\/babel\.min\.js"><\/script>\s*/gi, '');

    // 2. Change <script type="text/babel" src="..."> to <script src="...">
    html = html.replace(/<script\s+type="text\/babel"\s+src="([^"]+)"><\/script>/gi, '<script src="$1"></script>');

    // 3. Transpile inline <script type="text/babel"> to standard <script>
    const inlineBabelRegex = /<script\s+type="text\/babel">([\s\S]*?)<\/script>/gi;
    html = html.replace(inlineBabelRegex, (match, inlineCode) => {
      try {
        const transformedInline = Babel.transform(inlineCode, {
          presets: [['react', { runtime: 'classic' }]]
        }).code;
        return `<script>\n${transformedInline}\n  </script>`;
      } catch (err) {
        console.error(`  ✗ Error transpiling inline script in ${htmlFile}:`, err.message);
        return match;
      }
    });

    fs.writeFileSync(fullPath, html, 'utf8');
    console.log(`  ✓ Updated HTML: ${htmlFile}`);
  }

  console.log("\n4. Build complete! All files are now 100% portable for file:/// protocol.");
}

main().catch(console.error);
