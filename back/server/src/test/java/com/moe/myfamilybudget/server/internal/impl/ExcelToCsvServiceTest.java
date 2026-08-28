package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Date;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@DisplayName("ExcelToCsvService & convertExcelToCsv Tests")
class ExcelToCsvServiceTest {

    private ExcelToCsvService service;
    private StatementBankImportServiceImpl controller;

    @BeforeEach
    void setUp() {
        service = new ExcelToCsvService();
        PersistenceManager pm = new PersistenceManager();
        pm.init();
        StatementBankImportMapper mapper = new StatementBankImportMapper();
        controller = new StatementBankImportServiceImpl(pm, mapper, service);
    }

    @Test
    @DisplayName("Convert XLSX file with dates, numbers, and strings successfully")
    void testConvertXlsx() throws IOException {
        byte[] xlsxBytes = createSampleXlsx(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        String csv = service.convert(file, "releve.xlsx");

        assertThat(csv).isNotNull();
        String[] lines = csv.split("\r\n|\n");
        assertThat(lines).hasSizeGreaterThanOrEqualTo(3);
        assertThat(lines[0]).isEqualTo("Date;Libelle;Montant;Type");
        assertThat(lines[1]).contains("2026-01-15");
        assertThat(lines[1]).contains("Achat Leclerc; Drive"); // Escaped semicolon
        assertThat(lines[1]).contains("-45.5");
        assertThat(lines[2]).contains("2026-01-16");
        assertThat(lines[2]).contains("Virement Salaire");
        assertThat(lines[2]).contains("2500");
    }

    @Test
    @DisplayName("Convert XLS (legacy) file successfully")
    void testConvertXls() throws IOException {
        byte[] xlsBytes = createSampleXls();
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.xls",
                "application/vnd.ms-excel",
                xlsBytes);

        String csv = service.convert(file, "releve.xls");

        assertThat(csv).isNotNull();
        String[] lines = csv.split("\r\n|\n");
        assertThat(lines[0]).isEqualTo("Date;Libelle;Montant");
        assertThat(lines[1]).contains("2026-02-01;Prelevement EDF;-80");
    }

    @Test
    @DisplayName("Reject files exceeding 2500 data rows")
    void testExceedingMaxRows() throws IOException {
        byte[] largeXlsx = createLargeXlsx(2502); // 1 header + 2501 data rows
        MockMultipartFile file = new MockMultipartFile(
                "file", "trop_grand.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeXlsx);

        assertThatThrownBy(() -> service.convert(file, "trop_grand.xlsx"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depasse la limite autorisee de 2500 lignes");
    }

    @Test
    @DisplayName("Controller endpoint convertExcelToCsv returns 200 with text/plain CSV")
    void testControllerConvertEndpoint() throws IOException {
        byte[] xlsxBytes = createSampleXlsx(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        ResponseEntity<String> response = controller.convertExcelToCsv(file);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Date;Libelle;Montant;Type");
        assertThat(response.getBody()).contains("Achat Leclerc; Drive");
    }

    @Test
    @DisplayName("Importing Excel converted rows detects existing duplicates properly")
    void testExcelImportDuplicateDetection() throws IOException {
        byte[] xlsxBytes = createSampleXlsx(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "releve.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        String csv = service.convert(file, "releve.xlsx");
        java.util.List<java.util.List<String>> rows = com.moe.myfamilybudget.server.internal.model.BankImportCalculator.parseCSVText(csv, ";");
        java.util.List<java.util.List<String>> dataRows = rows.subList(1, rows.size());

        // Existing transactions in DB (with date "2026-01-15" and "2026-01-16")
        com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel existingTx =
                new com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel(
                        "tx_1", "2026-01-15", "Achat Leclerc; Drive", "CB", new java.math.BigDecimal("-45.50"), ""
                );

        com.moe.myfamilybudget.server.internal.model.BankImportModel.BankColumnMappingModel mapping =
                new com.moe.myfamilybudget.server.internal.model.BankImportModel.BankColumnMappingModel(
                        ";", "DD/MM/YYYY", true, 0, 1, 3, 2
                );

        com.moe.myfamilybudget.server.internal.model.BankImportSummaryModel summary =
                com.moe.myfamilybudget.server.internal.model.BankImportCalculator.importTransactions(
                        dataRows,
                        java.util.List.of("date", "label", "amount", "type"),
                        mapping,
                        java.util.List.of(existingTx),
                        java.util.Collections.emptyList()
                );

        // 1 duplicate correctly detected and ignored, 1 new transaction imported
        assertThat(summary.duplicates()).isEqualTo(1);
        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.newTransactions().get(0).label()).isEqualTo("Virement Salaire");
    }

    // --- Helpers ---

    private byte[] createSampleXlsx(boolean withQuotes) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Releve");
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Date");
            header.createCell(1).setCellValue("Libelle");
            header.createCell(2).setCellValue("Montant");
            header.createCell(3).setCellValue("Type");

            // Row 1
            Row r1 = sheet.createRow(1);
            Cell c0 = r1.createCell(0);
            c0.setCellValue(java.time.LocalDate.of(2026, 1, 15));
            c0.setCellStyle(dateStyle);

            r1.createCell(1).setCellValue("Achat Leclerc; Drive");
            r1.createCell(2).setCellValue(-45.50);
            r1.createCell(3).setCellValue("CB");

            // Row 2
            Row r2 = sheet.createRow(2);
            Cell c20 = r2.createCell(0);
            c20.setCellValue(java.time.LocalDate.of(2026, 1, 16));
            c20.setCellStyle(dateStyle);

            r2.createCell(1).setCellValue("Virement Salaire");
            r2.createCell(2).setCellValue(2500);
            r2.createCell(3).setCellValue("VIR");

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createSampleXls() throws IOException {
        try (Workbook wb = new HSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Feuille1");
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Date");
            header.createCell(1).setCellValue("Libelle");
            header.createCell(2).setCellValue("Montant");

            Row r1 = sheet.createRow(1);
            Cell c0 = r1.createCell(0);
            c0.setCellValue(java.time.LocalDate.of(2026, 2, 1));
            c0.setCellStyle(dateStyle);
            r1.createCell(1).setCellValue("Prelevement EDF");
            r1.createCell(2).setCellValue(-80.0);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createLargeXlsx(int rowCount) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Big");
            for (int i = 0; i < rowCount; i++) {
                Row r = sheet.createRow(i);
                r.createCell(0).setCellValue("2026-01-01");
                r.createCell(1).setCellValue("Ligne " + i);
                r.createCell(2).setCellValue(10.0);
            }
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}