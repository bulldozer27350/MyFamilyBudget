/**
 * Bandeau de disponibilité d'une nouvelle version, pour la distribution portable uniquement :
 * GET /update-check n'existe pas en profil Docker (voir UpdateCheckController, back/bootstrap),
 * BudgetApi.getUpdateStatus() renvoie alors null et ce bandeau ne s'affiche jamais.
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useEffect
  } = React;
  const {
    C
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    BudgetApi
  } = exports.BudgetApi ? exports : window.BudgetApp || {};

  /**
   * Bandeau compact affiché en haut de la zone de contenu, même emplacement que SyncStatusBanner.
   * Ne s'affiche que si une mise à jour est disponible et n'a pas été masquée par l'utilisateur
   * pour cette session (pas de persistance : réapparaît à la prochaine ouverture de l'application).
   */
  function UpdateAvailableBanner() {
    const [status, setStatus] = useState(null);
    const [dismissed, setDismissed] = useState(false);
    const [expanded, setExpanded] = useState(false);
    const [notesExpanded, setNotesExpanded] = useState(false);
    // idle | running | done | failed — état de la sauvegarde déclenchée par le clic sur Télécharger
    const [backupState, setBackupState] = useState('idle');
    const [backupPath, setBackupPath] = useState(null);

    useEffect(() => {
      let cancelled = false;
      if (BudgetApi && BudgetApi.getUpdateStatus) {
        BudgetApi.getUpdateStatus().then(s => {
          if (!cancelled && s && s.updateAvailable) setStatus(s);
        });
      }
      return () => {
        cancelled = true;
      };
    }, []);

    if (!status || dismissed) return null;

    const bg = C?.pineSoft || '#E3ECE8';
    const fg = C?.pine || '#2F5D50';
    const zipName = status.downloadUrl
      ? decodeURIComponent(status.downloadUrl.split('/').pop())
      : 'le zip de la nouvelle version';

    async function handleDownloadClick() {
      setBackupState('running');
      let result = null;
      try {
        result = BudgetApi && BudgetApi.backupBeforeUpdate ? await BudgetApi.backupBeforeUpdate() : null;
      } catch (e) {
        result = null;
      }
      if (result && result.success) {
        setBackupState('done');
        setBackupPath(result.backupPath || null);
      } else {
        setBackupState('failed');
      }
      // La sauvegarde n'est jamais bloquante : le téléchargement s'ouvre dans tous les cas.
      window.open(status.downloadUrl, '_blank', 'noopener');
    }

    const steps = [
      `Cliquez sur "Télécharger" ci-dessus : une sauvegarde de votre base est automatiquement créée avant l'ouverture du lien (voir le message qui s'affiche ensuite).`,
      `Fermez MyFamilyBudget si l'application est en cours d'exécution.`,
      `Une fois ${zipName} téléchargé, dézippez son contenu directement dans "${status.installDir}", en acceptant de remplacer les fichiers existants.`,
      `Relancez MyFamilyBudget.bat : vos données ne sont pas comprises dans le zip, elles sont conservées telles quelles.`
    ];

    return React.createElement('div', {
      style: {
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        padding: '9px 16px',
        marginBottom: 16,
        background: bg,
        color: fg,
        borderRadius: 8,
        border: `1px solid ${fg}`,
        fontSize: 13
      }
    },
      React.createElement('div', {
        style: {
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 10
        }
      },
        React.createElement('div', {
          style: { fontWeight: 600 }
        }, `Nouvelle version disponible : ${status.latestVersion} (version actuelle : ${status.currentVersion})`),
        React.createElement('div', {
          style: { display: 'flex', alignItems: 'center', gap: 14 }
        },
          status.downloadUrl && React.createElement('button', {
            type: 'button',
            onClick: handleDownloadClick,
            disabled: backupState === 'running',
            style: {
              background: 'none', border: 'none', color: fg, fontWeight: 600,
              cursor: backupState === 'running' ? 'default' : 'pointer',
              textDecoration: 'underline', padding: 0, fontSize: 13,
              opacity: backupState === 'running' ? 0.6 : 1
            }
          }, backupState === 'running' ? 'Sauvegarde en cours…' : 'Télécharger'),
          // REL-0xx : le corps de la GitHub Release reprend desormais la section
          // release-note.md de la version (release.yml), donc status.releaseNotes contient
          // ce texte - on l'affiche directement dans l'app plutot que de se contenter d'un
          // lien externe. Repli sur le lien externe si releaseNotes est absent (ancienne
          // release sans body, ou echec de recuperation cote backend).
          status.releaseNotes
            ? React.createElement('button', {
              type: 'button',
              onClick: () => setNotesExpanded(e => !e),
              style: {
                background: 'none', border: 'none', color: fg, fontWeight: 600,
                cursor: 'pointer', textDecoration: 'underline', padding: 0, fontSize: 13
              }
            }, notesExpanded ? 'Masquer les notes de version' : 'Notes de version')
            : status.releaseUrl && React.createElement('a', {
              href: status.releaseUrl,
              target: '_blank',
              rel: 'noopener noreferrer',
              style: { color: fg, fontWeight: 600, textDecoration: 'underline' }
            }, 'Notes de version'),
          React.createElement('button', {
            type: 'button',
            onClick: () => setExpanded(e => !e),
            style: {
              background: 'none', border: 'none', color: fg, fontWeight: 600,
              cursor: 'pointer', textDecoration: 'underline', padding: 0, fontSize: 13
            }
          }, expanded ? 'Masquer le guide' : 'Comment mettre à jour ?'),
          React.createElement('button', {
            type: 'button',
            onClick: () => setDismissed(true),
            'aria-label': 'Fermer',
            style: {
              background: 'none', border: 'none', color: fg,
              cursor: 'pointer', fontSize: 16, lineHeight: 1, padding: 0
            }
          }, '\u00D7')
        )
      ),
      backupState === 'done' && React.createElement('div', { style: { fontSize: 12 } },
        `Sauvegarde de votre base effectuée avant le téléchargement${backupPath ? ' (' + backupPath + ')' : ''}.`),
      backupState === 'failed' && React.createElement('div', { style: { fontSize: 12 } },
        `Échec de la sauvegarde automatique — votre base actuelle n'a pas été modifiée ; vous pouvez tout de même poursuivre la mise à jour.`),
      expanded && React.createElement('ol', {
        style: { margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 4 }
      }, steps.map((step, i) => React.createElement('li', { key: i }, step))),
      notesExpanded && status.releaseNotes && React.createElement('div', {
        style: {
          display: 'flex', flexDirection: 'column', gap: 6,
          maxHeight: 320, overflowY: 'auto',
          padding: '8px 10px', background: 'rgba(255,255,255,0.5)', borderRadius: 6
        }
      },
        // Texte source de release-note.md (Markdown non rendu) : pre-wrap suffit a restituer
        // les sauts de ligne et paragraphes sans ajouter de dependance a un parseur Markdown
        // pour ce simple affichage en consultation.
        React.createElement('div', {
          style: { whiteSpace: 'pre-wrap', fontSize: 12.5, lineHeight: 1.5 }
        }, status.releaseNotes),
        status.releaseUrl && React.createElement('a', {
          href: status.releaseUrl,
          target: '_blank',
          rel: 'noopener noreferrer',
          style: { color: fg, fontSize: 12, alignSelf: 'flex-start' }
        }, 'Voir sur GitHub')
      )
    );
  }

  exports.UpdateAvailableBanner = UpdateAvailableBanner;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
