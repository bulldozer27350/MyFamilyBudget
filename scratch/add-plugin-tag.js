const fs = require('fs');
const path = require('path');

const rootDir = path.resolve(__dirname, '..');
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

const annotationTag = '  <script src="https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@3.0.1/dist/chartjs-plugin-annotation.min.js"></script>';

for (const file of htmlFiles) {
  const filePath = path.join(rootDir, file);
  let content = fs.readFileSync(filePath, 'utf8');
  if (!content.includes('chartjs-plugin-annotation')) {
    content = content.replace(
      /(<script src="https:\/\/cdn\.jsdelivr\.net\/npm\/chart\.js@[^"]+"><\/script>)/,
      '$1\n' + annotationTag
    );
    fs.writeFileSync(filePath, content, 'utf8');
    console.log('Updated ' + file);
  } else {
    console.log('Already has plugin in ' + file);
  }
}
