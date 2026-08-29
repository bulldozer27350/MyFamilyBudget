//window.API_BASE_URL = "http://192.168.1.16:8080/api/v1"; // depuis le réseau wifi de la maison
//window.API_BASE_URL = "http://172.25.65.15:8080/api/v1"; // depuis le partage de co du téléphone
//window.API_BASE_URL = "http://100.106.122.11:8080/api/v1"; // depuis internet
window.API_BASE_URL = "http://127.0.0.1:8080/api/v1"; // Depuis du local

// Mode diagnostic : quand true, les appels de view/js/api.js qui echouent (backend
// injoignable ou reponse HTTP non-2xx) ne basculent plus silencieusement sur le
// service JS local (service-metier.js / localStorage). Ils remontent l'erreur dans
// la console et rejettent la Promise, pour verifier sans ambiguite que les donnees
// affichees proviennent bien de la base H2 servie par Spring Boot.
// A repasser a false une fois le diagnostic termine : sans backend actif, les
// onglets concernes (Vue d'ensemble, Tresorerie, Patrimoine, Parametres) resteront
// alors bloques sur "Chargement...".
window.DISABLE_JS_FALLBACK = false;