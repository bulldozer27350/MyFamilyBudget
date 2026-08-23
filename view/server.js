const express = require('express');
const path = require('path');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = 3000;
// Définition de l'adresse et du port du serveur Spring Boot back-end
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

// Proxy API requests to Spring Boot backend server (port 8080)
const apiPaths = [
  '/overview',
  '/tresorerie',
  '/patrimoine',
  '/retraite',
  '/impots',
  '/settings',
  '/budget',
  '/bank-import',
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
  target: 'http://localhost:8080/api/v1',
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


