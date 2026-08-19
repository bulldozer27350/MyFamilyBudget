const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;

// Serve static assets from root directory
app.use(express.static(path.join(__dirname)));

// Fallback to overview.html or index.html
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'overview.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`MyFamilyBudget server running on http://0.0.0.0:${PORT}`);
});
