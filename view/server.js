const express = require('express');
const path = require('path');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = 3000;

// === TA CONSTANTE UNIQUE POUR LE SERVEUR NODE ===
const TARGET_SPRINGBOOT = 'http://172.25.65.15:8080'; // Renseigne ton IP locale ici

const BACKEND_URL = process.env.BACKEND_URL || `${TARGET_SPRINGBOOT}/api/v1`;

// Proxy API requests to Spring Boot backend server
const apiPaths = [
  '/overview',
  '/tresorerie',
  '/patrimoine',
  '/retraite',
  '/impots',
  '/settings',
  '/budget',
  '/bank-import',
  '/bank',
  '/pending-operations',
  '/pointage',
  '/analyse'
];

apiPaths.forEach(apiPath => {
  app.use(apiPath, createProxyMiddleware({
    target: BACKEND_URL,
    changeOrigin: true,
    logLevel: 'warn'
  }));
});

app.use('/api/v1', createProxyMiddleware({
  target: TARGET_SPRINGBOOT, // <- Remplacement du localhost en dur
  changeOrigin: true,
  pathRewrite: { '^/api/v1': '' },
  logLevel: 'warn'
}));

// Serve static assets from view directory
app.use(express.static(__dirname));

// Fallback to overview.html or index.html in view
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'overview.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`MyFamilyBudget server running on http://0.0.0.0:${PORT}`);
  console.log(`Backend API proxied to ${BACKEND_URL}`);
});


