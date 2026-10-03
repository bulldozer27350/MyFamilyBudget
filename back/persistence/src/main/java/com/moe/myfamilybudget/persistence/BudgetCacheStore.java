package com.moe.myfamilybudget.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;

/**
 * Cache mémoire du budget courant et point d'entrée unique pour toute mutation qui doit être
 * persistée.
 *
 * Deuxième incrément du Strangler Fig prévu par le point 6 de l'audit (God Class
 * {@code PersistenceManager}). Reprend, à l'identique, la logique qui vivait auparavant dans
 * {@code PersistenceManager} ({@code currentBudget}, {@code mutationLock}, {@code applyAndPersist},
 * {@code init}, {@code getBudgetData}, {@code setBudgetData}, {@code resetData},
 * {@code createDefaultBudgetData}) : aucun changement de comportement fonctionnel, uniquement un
 * déplacement de code. Délègue tout accès base à {@link BudgetPersistenceGateway} (1er incrément).
 *
 * Volontairement une classe simple (pas un bean Spring), instanciée directement par
 * {@code PersistenceManager} dans son constructeur — voir la note à ce sujet dans
 * {@link BudgetPersistenceGateway}.
 *
 * Ce qui reste dans {@code PersistenceManager} pour l'instant (prochain et dernier incrément du
 * point 6) : toute la logique métier des mutations (dispatcher du point 3, gestion des lignes de
 * trésorerie/patrimoine, retraite, fiscalité...), qui appellera {@link #applyAndPersist} et
 * {@link #createDefaultBudgetData()} exactement comme {@code PersistenceManager} le fait
 * aujourd'hui.
 */
class BudgetCacheStore {

    private final BudgetPersistenceGateway gateway;
    private final TransactionTemplate transactionTemplate;

    private final AtomicReference<BudgetDataModel> currentBudget = new AtomicReference<>();

    // Verrou dédié aux mutations : protège la séquence lecture -> mutation -> écriture contre les pertes
    // de mise à jour en cas de requêtes concurrentes (deux appels simultanés partant tous les deux du
    // même état "current" et écrasant l'un le résultat de l'autre). AtomicReference garantit uniquement
    // l'atomicité d'une affectation individuelle, pas celle de la séquence complète.
    //
    // VT-350b : quand une transaction est active, le verrou est conservé jusqu'à la FIN de cette
    // transaction (commit ou rollback), pas seulement pendant l'appel. Relâché plus tôt, une seconde
    // mutation lisait la base (et chargeait des entités JPA comme BankImportEntity) pendant que la
    // première n'était pas encore validée, puis tentait de supprimer des lignes déjà remplacées :
    // StaleObjectStateException -> HTTP 500. ReentrantLock (et non synchronized) car il est relâché
    // depuis afterCompletion, hors du bloc qui l'a pris.
    private final ReentrantLock mutationLock = new ReentrantLock();

    // Clé de la ressource liée à la transaction en cours : présente tant que cette transaction détient
    // le verrou ; porte l'état mémoire d'AVANT sa première mutation (voir enlistInCurrentTransaction).
    private final Object rollbackSnapshotKey = new Object();

    private record RollbackSnapshot(BudgetDataModel previous) {
    }

    BudgetCacheStore(BudgetPersistenceGateway gateway, TransactionTemplate transactionTemplate) {
        this.gateway = gateway;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Charge l'état initial au démarrage de l'application (appelé depuis le {@code @PostConstruct}
     * de {@code PersistenceManager}, dans le même thread donc avant toute requête HTTP).
     */
    void init() {
        // Try to load from database first.
        //
        // NOTE: comme pour gateway.save(...) plus bas, ce bloc s'exécute en self-invocation
        // depuis @PostConstruct, donc hors du proxy AOP @Transactional de la classe
        // PersistenceManager. Sans transaction explicite, la connexion JDBC reste en autocommit,
        // ce qui fait échouer la lecture paresseuse de BankImportEntity.jsonData (mappé en Large
        // Object côté PostgreSQL) avec "Large Objects may not be used in auto-commit mode" — mais
        // uniquement dès qu'une ligne bank_import existe déjà en base. Le tout premier démarrage
        // sur une base vide ne déclenche jamais ce chemin (existingData est alors absent), d'où
        // un bug invisible en test initial et bloquant dès le redémarrage suivant. On ouvre donc
        // explicitement une transaction programmatique autour de la lecture, au même titre que
        // l'écriture.
        //
        // IMPORTANT : la requête findFirstByOrderByIdAsc() DOIT elle-même s'exécuter à
        // l'intérieur de cette transaction, pas avant. Un appel de méthode de repository Spring
        // Data s'exécute par défaut dans sa propre transaction, qui se termine dès qu'il
        // retourne : l'entité obtenue serait alors détachée avant même d'atteindre
        // transactionTemplate.execute(), et toute collection LAZY qu'elle porte (ex.
        // l'ancienne RetirementEntity.people) échouerait au premier accès avec "could not initialize proxy -
        // no Session", une nouvelle transaction ne rattachant pas rétroactivement une entité déjà
        // détachée d'une session précédente.
        BudgetDataModel complete = (transactionTemplate != null)
                ? transactionTemplate.execute(status -> gateway.loadExistingIfPresent())
                : gateway.loadExistingIfPresent();
        if (complete != null) {
            currentBudget.set(complete);
            return;
        }

        // Create new default data and save to database.
        // NOTE: init() est appelé depuis le callback @PostConstruct de PersistenceManager,
        // invoqué par Spring AVANT que le proxy AOP (@Transactional) n'enveloppe ce bean.
        // L'appel ci-dessous à gateway.save(...) est donc un self-invocation qui NE PASSE PAS
        // par le proxy transactionnel de la classe : sans la mesure explicite ci-dessous, les
        // opérations d'écriture déclenchées (notamment loanRepository.deleteByBudgetDataId,
        // qui exécute une requête JPQL de suppression) échouent avec
        // "TransactionRequiredException: Executing an update/delete query" dès que la
        // base est vide au démarrage (typiquement en environnement de test, où le schéma
        // est réinitialisé). On ouvre donc explicitement une transaction programmatique.
        BudgetDataModel defaultData = createDefaultBudgetData();
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> gateway.save(defaultData));
        } else {
            gateway.save(defaultData);
        }
        currentBudget.set(defaultData);
    }

    /**
     * Point d'entrée unique pour toute mutation du budget qui doit être persistée.
     *
     * Contrat volontairement différent du pattern "currentBudget.updateAndGet(...)" suivi d'une
     * sauvegarde séparée : ici, la mémoire (currentBudget) n'est mise à jour QU'APRES que la
     * sauvegarde en base a réussi. Si gateway.save(...) lève une exception (contrainte SQL, perte
     * de connexion...), le rollback transactionnel de la base est cohérent avec l'état conservé en
     * mémoire : aucun des deux n'a été modifié. Avec l'ancien pattern, la mémoire était modifiée
     * avant même de savoir si la sauvegarde allait réussir, ce qui pouvait la laisser durablement
     * désynchronisée de la base après une erreur.
     *
     * @param mutation fonction pure calculant le nouvel état à partir de l'état courant
     *                 (ou d'un budget par défaut si aucun état n'existe encore). Ne doit
     *                 provoquer aucun effet de bord (pas d'accès base, pas d'écriture sur
     *                 currentBudget) : seule cette méthode a le droit d'écrire dans
     *                 currentBudget.
     * @return le nouvel état, déjà persisté et déjà visible depuis getBudgetData()
     */
    BudgetDataModel applyAndPersist(UnaryOperator<BudgetDataModel> mutation) {
        return underMutationLock(() -> {
            BudgetDataModel base = currentBudget.get();
            if (base == null) {
                base = createDefaultBudgetData();
            }
            BudgetDataModel updated = mutation.apply(base);

            // Persistée AVANT toute écriture sur currentBudget : si ça échoue, on sort par
            // exception sans jamais avoir touché à la mémoire.
            gateway.save(updated);

            currentBudget.set(updated);
            return updated;
        });
    }

    /**
     * Récupère le modèle de budget complet depuis la base de données.
     *
     * Double-checked locking sur {@link #mutationLock} (même verrou qu'{@link #applyAndPersist}) :
     * le chemin rapide (budget déjà chargé, cas de très loin le plus fréquent puisque
     * {@code currentBudget} n'est plus jamais {@code null} après le démarrage) reste hors verrou
     * pour ne pas payer de synchronisation sur un getter appelé en permanence. Seul le chemin lent
     * (premier chargement, ou juste après un {@link #resetData()}) prend le verrou, pour éviter
     * qu'un chargement/création concurrent ne double-crée un budget par défaut en base — voir le
     * point 5 de l'audit : {@code AtomicReference} ne rend pas atomique la séquence complète
     * "lire, décider, écrire", seulement chacune de ses affectations individuelles.
     */
    BudgetDataModel getBudgetData() {
        BudgetDataModel model = currentBudget.get();
        if (model != null) {
            return model;
        }
        // Verrou pris pour la seule durée de ce chargement (pas conservé jusqu'à la fin d'une
        // éventuelle transaction englobante : ce n'est pas une mutation de l'état courant).
        mutationLock.lock();
        try {
            // Un autre thread a pu déjà effectuer le chargement/création pendant qu'on attendait
            // le verrou : on revérifie avant de refaire le travail.
            model = currentBudget.get();
            if (model != null) {
                return model;
            }
            // Reload from database
            model = gateway.loadExistingIfPresent();

            if (model == null) {
                model = createDefaultBudgetData();
                gateway.save(model);
            }
            currentBudget.set(model);
            return model;
        } finally {
            mutationLock.unlock();
        }
    }

    /**
     * Remplace l'intégralité du modèle de données (utilisé lors de l'import JSON).
     *
     * Sous {@link #mutationLock} (point 5 de l'audit) : sans ce verrou, un import
     * concurrent d'une mutation passant par {@link #applyAndPersist} pourrait avoir lu l'ancien
     * état juste avant cet appel et écraser ensuite en base, avec son propre {@code gateway.save},
     * le résultat de cet import — perte silencieuse de l'import.
     */
    void setBudgetData(BudgetDataModel data) {
        underMutationLock(() -> {
            BudgetDataModel toStore = data != null ? data : createDefaultBudgetData();
            gateway.save(toStore);
            currentBudget.set(toStore);
            return null;
        });
    }

    /**
     * Réinitialise les données aux valeurs par défaut.
     *
     * Sous {@link #mutationLock} (point 5 de l'audit), pour la même raison que
     * {@link #setBudgetData}: sans le verrou, une mutation concurrente en cours via
     * {@link #applyAndPersist} pourrait persister son propre résultat juste après ce
     * {@code deleteAll()}, ressuscitant les données que resetData() venait d'effacer.
     */
    BudgetDataModel resetData() {
        return underMutationLock(() -> {
            // Clear existing data
            gateway.deleteAll();

            BudgetDataModel defaultData = createDefaultBudgetData();
            gateway.save(defaultData);
            currentBudget.set(defaultData);
            return defaultData;
        });
    }

    /**
     * Prend dès maintenant, pour toute la durée de la transaction en cours, le verrou que prendrait
     * la première mutation (VT-350b). À appeler en PREMIER dans une façade {@code @Transactional}
     * multi-domaines, avant toute écriture dans un autre domaine : le verrou de mutation passe ainsi
     * toujours avant les verrous de lignes de la base, ce qui écarte l'interblocage entre une
     * transaction qui tiendrait déjà une ligne (ex. paramètres Objectifs) et attendrait le verrou, et
     * une autre qui tiendrait le verrou et attendrait cette ligne. Sans transaction active, ne fait rien.
     */
    void lockForCurrentTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            enlistInCurrentTransaction();
        }
    }

    /**
     * Exécute une mutation sous {@link #mutationLock}.
     *
     * <p>Avec une transaction active (cas de production : {@code PersistenceManager} est
     * {@code @Transactional}), le verrou est conservé jusqu'à la fin de la transaction : voir
     * {@link #enlistInCurrentTransaction}. Sans transaction (tests unitaires sur repositories
     * mockés), il est relâché dès le retour de {@code action}.
     */
    private <T> T underMutationLock(Supplier<T> action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            enlistInCurrentTransaction();
            return action.get();
        }
        mutationLock.lock();
        try {
            return action.get();
        } finally {
            mutationLock.unlock();
        }
    }

    /**
     * Rattache la transaction en cours au verrou de mutation, une seule fois par transaction
     * (VT-350b, étend VT-340).
     *
     * <p>Verrou : pris ici et relâché dans {@code afterCompletion}, donc après le commit ou le
     * rollback. La mutation suivante part ainsi toujours d'un état en base validé et d'un contexte
     * JPA neuf ; elle ne peut plus charger des entités qu'une transaction concurrente est sur le
     * point de supprimer ou de remplacer (StaleObjectStateException).
     *
     * <p>Mémoire : on mémorise l'état d'avant la première mutation de la transaction et on le
     * rétablit si elle ne se termine pas par un commit (VT-340). Si un appel ultérieur de la MÊME
     * transaction échoue (autre domaine, échec du commit...), la base est annulée mais la mémoire
     * serait restée sur le nouvel état. La restauration a lieu avant la libération du verrou.
     */
    private void enlistInCurrentTransaction() {
        if (TransactionSynchronizationManager.hasResource(rollbackSnapshotKey)) {
            return; // cette transaction détient déjà le verrou (ré-entrance)
        }
        mutationLock.lock();
        try {
            TransactionSynchronizationManager.bindResource(rollbackSnapshotKey, new RollbackSnapshot(currentBudget.get()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        Object snapshot = TransactionSynchronizationManager.unbindResourceIfPossible(rollbackSnapshotKey);
                        if (snapshot instanceof RollbackSnapshot restore && status != STATUS_COMMITTED) {
                            currentBudget.set(restore.previous());
                        }
                    } finally {
                        mutationLock.unlock();
                    }
                }
            });
        } catch (RuntimeException | Error e) {
            TransactionSynchronizationManager.unbindResourceIfPossible(rollbackSnapshotKey);
            mutationLock.unlock();
            throw e;
        }
    }

    /**
     * Construit un budget par défaut (utilisé au tout premier démarrage, et comme état de base
     * pour toute mutation lorsque {@code currentBudget} est encore vide). Fonction pure, sans
     * effet de bord.
     */
    BudgetDataModel createDefaultBudgetData() {
        SettingsModel settings = new SettingsModel(
                1985,
                64,
                85,
                new BigDecimal("0.02"),
                "",
                "manual",
                BigDecimal.ZERO,
                21,
                new BigDecimal("0.10"),
                false,
                null,
                null
        );

        RetirementModel retirement = new RetirementModel(
                Collections.emptyList(),
                new BigDecimal("47100"),
                new BigDecimal("0.015"),
                new BigDecimal("1.4386"),
                "2025-11-01",
                new BigDecimal("0.01")
        );

        List<TaxBracketModel> taxBrackets = List.of(
                new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
                new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
                new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
                new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
                new TaxBracketModel("tb_5", null, new BigDecimal("0.45"))
        );

        BankImportModel bankImport = new BankImportModel(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        return new BudgetDataModel(
                settings,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                retirement,
                new ArrayList<>(),
                taxBrackets,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                bankImport,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
