const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;

// Serve static assets from view directory
app.use(express.static(__dirname));

// Fallback to overview.html or index.html in view
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'overview.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`MyFamilyBudget server running on http://0.0.0.0:${PORT}`);
});

