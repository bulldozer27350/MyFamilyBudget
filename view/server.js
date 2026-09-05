const express = require('express');
const path = require('path');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = 3000;

// === TA CONSTANTE UNIQUE POUR LE SERVEUR NODE ===
const TARGET_SPRINGBOOT = process.env.TARGET_SPRINGBOOT || 'http://localhost:8080';

const BACKEND_URL = process.env.BACKEND_URL || `${TARGET_SPRINGBOOT}/api/v1`;

// Proxy API requests to Spring Boot backend server.
//
// Le front (view/config.js) construit desormais systematiquement ses appels
// avec le prefixe "/api/v1" (window.API_BASE_URL). Or, quand un middleware
// est monte via app.use('/api/v1', ...), Express retire ce prefixe de
// req.url AVANT d'invoquer le middleware : http-proxy-middleware (>= v2)
// ne le restaure plus automatiquement (contrairement a l'ancienne notion de
// "context" des versions v0.x/v1.x). Sans pathRewrite pour le rajouter, la
// requete relayee vers Spring Boot perdait son prefixe "/api/v1" (attendu
// par le context-path du profil par defaut) et le backend repondait 404
// (observe sur /api/v1/overview et /api/v1/budget/reset en local et en CI
// GitHub Actions).
app.use('/api/v1', createProxyMiddleware({
  target: TARGET_SPRINGBOOT,
  changeOrigin: true,
  pathRewrite: (p) => '/api/v1' + p,
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


