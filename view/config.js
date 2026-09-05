// Detection automatique du contexte plutot qu une URL absolue figee :
// - Profil "docker" (prod, mini-PC) : Spring Boot sert le front ET l API sous
//   le context-path "/myfamilybudget" (voir application-docker.yml). Des que
//   la page est chargee depuis ce prefixe, on tape en relatif dessus.
// - Profil par defaut (dev local, CI GitHub Actions) : le serveur Node
//   view/server.js sert le front en "/" et proxifie "/api/v1" vers Spring
//   Boot (voir view/server.js). On retombe alors sur ce prefixe.
// Comme c est toujours du relatif (meme origine que la page chargee), ca
// fonctionne sans changement quel que soit le reseau emprunte pour atteindre
// le serveur (wifi maison, partage de connexion telephone, Tailscale...) :
// plus besoin de choisir/decommenter une adresse a la main avant chaque usage.
window.API_BASE_URL = window.location.pathname.indexOf("/myfamilybudget") === 0
  ? "/myfamilybudget"
  : "/api/v1";

// Mode diagnostic : quand true, les appels de view/js/api.js qui echouent (backend
// injoignable ou reponse HTTP non-2xx) ne basculent plus silencieusement sur le
// service JS local (service-metier.js / localStorage). Ils remontent l'erreur dans
// la console et rejettent la Promise, pour verifier sans ambiguite que les donnees
// affichees proviennent bien de la base H2 servie par Spring Boot.
// A repasser a false une fois le diagnostic termine : sans backend actif, les
// onglets concernes (Vue d'ensemble, Tresorerie, Patrimoine, Parametres) resteront
// alors bloques sur "Chargement...".
window.DISABLE_JS_FALLBACK = false;