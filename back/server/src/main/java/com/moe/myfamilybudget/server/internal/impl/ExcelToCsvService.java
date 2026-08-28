package com.moe.myfamilybudget.server.internal.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service de conversion fichier Excel (.xls / .xlsx) -> texte CSV.
 *
 * <p>Regles de conversion :
 * <ul>
 *   <li>Seule la premiere feuille du classeur est traitee.</li>
 *   <li>Maximum {@value #MAX_DATA_ROWS} lignes de donnees (hors eventuelle ligne d'en-tete).</li>
 *   <li>Separateur de colonnes : point-virgule « ; ».</li>
 *   <li>Les cellules contenant une virgule, un point-virgule ou un guillemet sont encadrees par des guillemets doubles.</li>
 *   <li>Les valeurs numeriques sont converties sans notation scientifique.</li>
 *   <li>Les dates sont converties au format ISO yyyy-MM-dd.</li>
 * </ul>
 */
@Service
public class ExcelToCsvService {

    /** Nombre maximum de lignes de donnees acceptees (hors header). */
    public static final int MAX_DATA_ROWS = 2500;

    /**
     * Convertit le fichier Excel recu en texte CSV (separateur « ; »).
     *
     * @param file     le fichier multipart envoye par le client
     * @param filename nom original du fichier (utilise pour detecter .xls vs .xlsx)
     * @return texte CSV complet
     * @throws IOException              en cas d'erreur de lecture
     * @throws IllegalArgumentException si le format n'est pas supporte ou si le fichier depasse la limite de lignes
     */
    public String convert(MultipartFile file, String filename) throws IOException {
        String lower = (filename == null ? "" : filename).toLowerCase();
        try (InputStream is = file.getInputStream();
             Workbook workbook = lower.endsWith(".xlsx") ? new XSSFWorkbook(is) : openXls(is, lower)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getPhysicalNumberOfRows();

            // La premiere ligne peut etre un en-tete ; on compte les lignes de donnees
            int dataRows = Math.max(0, totalRows - 1);
            if (dataRows > MAX_DATA_ROWS) {
                throw new IllegalArgumentException(
                        "Le fichier contient " + dataRows + " lignes de donnees, "
                        + "ce qui depasse la limite autorisee de " + MAX_DATA_ROWS + " lignes.");
            }

            StringWriter sw = new StringWriter();
            for (Row row : sheet) {
                if (row == null) continue;
                short lastCell = row.getLastCellNum();
                for (int c = 0; c < lastCell; c++) {
                    if (c > 0) sw.append(';');
                    sw.append(escapeCsv(cellToString(row.getCell(c))));
                }
                sw.append("\r\n");
            }
            return sw.toString();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers prives
    // -----------------------------------------------------------------------

    private Workbook openXls(InputStream is, String lower) throws IOException {
        if (!lower.endsWith(".xls")) {
            throw new IllegalArgumentException(
                    "Format de fichier non supporte. Formats acceptes : .csv, .xls, .xlsx");
        }
        return new HSSFWorkbook(is);
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Format ISO yyyy-MM-dd
                    java.time.LocalDate ld = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield ld.toString();
                }
                double v = cell.getNumericCellValue();
                // Eviter la notation scientifique pour les grands entiers
                if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK   -> "";
            default      -> "";
        };
    }

    /**
     * Entoure la valeur de guillemets doubles si elle contient un separateur,
     * un guillemet ou un saut de ligne. Les guillemets internes sont doubles.
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}