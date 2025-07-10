package com.javallm.services;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelTextExtractorService {

    // Optimal chunk size based on a 512 token limit (512 * ~4 chars/token, with a buffer)
    private static final int CHUNK_SIZE = 700;
    // Overlap to maintain context between chunks
    private static final int CHUNK_OVERLAP = 200;

    /**
     * A record to hold a chunk of text and its corresponding sheet/section information.
     */
    public record TextChunk(String content, String sectionLabel) {
    }

    /**
     * A private record to temporarily hold cell content and its location.
     */
    private record CellContent(String text, String sheetName, int rowIndex, int cellIndex) {
    }

    /**
     * Extracts text from an Excel document (.xls, .xlsx, .xlsm, .xlsb), then splits it into
     * overlapping chunks.
     *
     * @param inputStream The InputStream of the Excel document file.
     * @param filename    The filename to determine the document type.
     * @return A List of TextChunks, each containing a piece of text and its section metadata.
     * @throws IOException if the document cannot be loaded or read.
     */
    public List<TextChunk> extractAndSplitText(InputStream inputStream, String filename) throws IOException {
        List<CellContent> cellContents = new ArrayList<>();
        String extension = getFileExtension(filename);

        switch (extension) {
            case "xlsx":
            case "xlsm":
            case "xlsb":
                cellContents = extractFromXlsx(inputStream);
                break;
            case "xls":
                cellContents = extractFromXls(inputStream);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file format. Only .xls, .xlsx, .xlsm, and .xlsb files are supported.");
        }

        return splitTextIntoChunks(cellContents);
    }

    /**
     * Extracts the file extension from a filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1).toLowerCase() : "";
    }

    /**
     * Extracts text from .xlsx, .xlsm, or .xlsb files using Apache POI XSSF.
     */
    private List<CellContent> extractFromXlsx(InputStream inputStream) throws IOException {
        List<CellContent> cellContents = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            extractCellContents(workbook, cellContents);
        }

        return cellContents;
    }

    /**
     * Extracts text from .xls files using Apache POI HSSF.
     */
    private List<CellContent> extractFromXls(InputStream inputStream) throws IOException {
        List<CellContent> cellContents = new ArrayList<>();

        try (Workbook workbook = new HSSFWorkbook(inputStream)) {
            extractCellContents(workbook, cellContents);
        }

        return cellContents;
    }

    /**
     * Common method to extract cell contents from any workbook type.
     */
    private void extractCellContents(Workbook workbook, List<CellContent> cellContents) {
        DataFormatter dataFormatter = new DataFormatter();
        FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            String sheetName = sheet.getSheetName();

            for (Row row : sheet) {
                for (Cell cell : row) {
                    String cellText = getCellValueAsString(cell, dataFormatter, formulaEvaluator);
                    if (cellText != null && !cellText.trim().isEmpty()) {
                        cellContents.add(new CellContent(
                            cellText.trim(), 
                            sheetName, 
                            row.getRowNum() + 1, // 1-based row index
                            cell.getColumnIndex() + 1 // 1-based column index
                        ));
                    }
                }
            }
        }
    }

    /**
     * Extracts the string value from a cell, handling different cell types including formulas.
     */
    private String getCellValueAsString(Cell cell, DataFormatter dataFormatter, FormulaEvaluator formulaEvaluator) {
        if (cell == null) {
            return "";
        }

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return dataFormatter.formatCellValue(cell);
                    } else {
                        return dataFormatter.formatCellValue(cell);
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        CellValue cellValue = formulaEvaluator.evaluate(cell);
                        return getCellValueFromCellValue(cellValue, dataFormatter);
                    } catch (Exception e) {
                        // If formula evaluation fails, return the formula string
                        return cell.getCellFormula();
                    }
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            // If any error occurs, try to get the formatted value
            return dataFormatter.formatCellValue(cell);
        }
    }

    /**
     * Extracts string value from a CellValue object (used for formula evaluation).
     */
    private String getCellValueFromCellValue(CellValue cellValue, DataFormatter dataFormatter) {
        if (cellValue == null) {
            return "";
        }

        switch (cellValue.getCellType()) {
            case STRING:
                return cellValue.getStringValue();
            case NUMERIC:
                return String.valueOf(cellValue.getNumberValue());
            case BOOLEAN:
                return String.valueOf(cellValue.getBooleanValue());
            case ERROR:
                return FormulaError.forInt(cellValue.getErrorValue()).getString();
            default:
                return "";
        }
    }

    /**
     * Splits the text content from all cells into manageable chunks using a sliding window.
     */
    private List<TextChunk> splitTextIntoChunks(List<CellContent> cellContents) {
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();
        List<Integer> cellStartIndices = new ArrayList<>();

        // Group cells by sheet and create a readable text format
        String currentSheet = "";
        for (CellContent cellContent : cellContents) {
            if (!cellContent.sheetName().equals(currentSheet)) {
                if (!currentSheet.isEmpty()) {
                    fullTextBuilder.append("\n\n"); // Add extra spacing between sheets
                }
                currentSheet = cellContent.sheetName();
                cellStartIndices.add(fullTextBuilder.length());
                fullTextBuilder.append("Sheet: ").append(currentSheet).append("\n");
            }
            
            cellStartIndices.add(fullTextBuilder.length());
            fullTextBuilder.append("R").append(cellContent.rowIndex())
                           .append("C").append(cellContent.cellIndex())
                           .append(": ").append(cellContent.text()).append("\n");
        }

        String fullText = fullTextBuilder.toString();
        if (fullText.isEmpty()) {
            return chunks;
        }

        int textLength = fullText.length();
        int start = 0;

        // Use a sliding window to create chunks with overlap.
        while (start < textLength) {
            int end = Math.min(start + CHUNK_SIZE, textLength);

            // To avoid cutting words in half, find the last space before the hard limit.
            if (end < textLength) {
                int lastSpace = fullText.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            String chunkText = fullText.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                String sectionLabel = getSectionLabelForChunk(start, end, cellStartIndices, cellContents);
                chunks.add(new TextChunk(chunkText, sectionLabel));
            }

            // Move the window forward, subtracting the overlap.
            start += CHUNK_SIZE - CHUNK_OVERLAP;

            // Ensure we always make forward progress.
            if (start >= end) {
                start = end;
            }
        }
        return chunks;
    }

    /**
     * Determines the sheet and cell range for a given text chunk.
     */
    private String getSectionLabelForChunk(int chunkStart, int chunkEnd, List<Integer> cellStartIndices, 
                                         List<CellContent> cellContents) {
        if (cellContents.isEmpty()) {
            return "N/A";
        }

        String startSheet = null;
        String endSheet = null;
        int startCellIndex = -1;
        int endCellIndex = -1;

        for (int i = 0; i < cellStartIndices.size() && i < cellContents.size(); i++) {
            int cellStartIndex = cellStartIndices.get(i);
            int nextCellStartIndex = (i + 1 < cellStartIndices.size()) ? cellStartIndices.get(i + 1) : Integer.MAX_VALUE;

            // Check if the chunk overlaps with the current cell's text span.
            if (chunkStart < nextCellStartIndex && chunkEnd > cellStartIndex) {
                if (startCellIndex == -1) {
                    startCellIndex = i;
                    startSheet = cellContents.get(i).sheetName();
                }
                endCellIndex = i;
                endSheet = cellContents.get(i).sheetName();
            }
        }

        if (startCellIndex == -1) {
            return "N/A";
        }

        if (startSheet.equals(endSheet)) {
            if (startCellIndex == endCellIndex) {
                CellContent cell = cellContents.get(startCellIndex);
                return String.format("%s[R%dC%d]", startSheet, cell.rowIndex(), cell.cellIndex());
            } else {
                CellContent startCell = cellContents.get(startCellIndex);
                CellContent endCell = cellContents.get(endCellIndex);
                return String.format("%s[R%dC%d-R%dC%d]", startSheet, 
                                   startCell.rowIndex(), startCell.cellIndex(),
                                   endCell.rowIndex(), endCell.cellIndex());
            }
        } else {
            return String.format("%s-%s[Multi-sheet]", startSheet, endSheet);
        }
    }
}