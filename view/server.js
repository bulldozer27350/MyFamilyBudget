const express = require('express');
const path = require('path');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = 3000;

// === TA CONSTANTE UNIQUE POUR LE SERVEUR NODE ===
const TARGET_SPRINGBOOT = process.env.TARGET_SPRINGBOOT || 'http://localhost:8080';

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
    target: TARGET_SPRINGBOOT,
    changeOrigin: true,
    pathRewrite: (p) => '/api/v1' + p,
    logLevel: 'warn'
  }));
});

app.use('/api/v1', createProxyMiddleware({
  target: TARGET_SPRINGBOOT,
  changeOrigin: true,
  logLevel: 'warn'
}));

// Heartbeat endpoint
app.get(['/heartbeat', '/ping', '/api/v1/heartbeat', '/api/v1/ping'], (req, res) => {
  res.json({ status: 'UP', app: 'MyFamilyBudget', version: '1.0.0', timestamp: Date.now() });
});

// Serve static assets from view directory
app.use(express.static(__dirname));

// Fallback to index.html (Web SplashScreen)
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`MyFamilyBudget server running on http://0.0.0.0:${PORT}`);
  console.log(`Backend API proxied to ${BACKEND_URL}`);
});


