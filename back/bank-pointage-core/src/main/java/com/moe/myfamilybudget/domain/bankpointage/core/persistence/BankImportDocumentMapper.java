package com.moe.myfamilybudget.domain.bankpointage.core.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;

/**
 * Mapper du domaine Banque entre {@link BankImportModel} et {@link BankImportDocumentEntity} (DB-1030).
 *
 * <p>Additif : {@code EntityModelConverter} et {@code BudgetPersistenceGateway} ne sont pas modifies. Meme
 * configuration Jackson que le chemin legacy (proprietes inconnues ignorees : le format JSON reste evolutif).
 * Contrairement au chemin legacy, qui journalise l'erreur et continue, une erreur de (de)serialisation est ici
 * <strong>propagee</strong> ({@link IllegalStateException}) : le choix de tolerer ou non une erreur releve de
 * la bascule (DB-1031).
 */
public final class BankImportDocumentMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private BankImportDocumentMapper() {}

    public static BankImportDocumentEntity toEntity(BankImportModel model) {
        if (model == null) {
            return null;
        }
        try {
            return new BankImportDocumentEntity(OBJECT_MAPPER.writeValueAsString(model));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialisation de l'import bancaire impossible", e);
        }
    }

    /**
     * @return le modele, ou {@code null} si l'entite est {@code null} ou ne porte aucun contenu
     */
    public static BankImportModel toModel(BankImportDocumentEntity entity) {
        if (entity == null || entity.getJsonData() == null || entity.getJsonData().isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(entity.getJsonData(), BankImportModel.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Lecture de l'import bancaire impossible", e);
        }
    }
}
