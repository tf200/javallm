// package com.javallm.test;

// import com.javallm.services.PdfTextExtractorService;
// import java.io.FileInputStream;
// import java.io.FileWriter;
// import java.io.IOException;
// import java.io.InputStream;
// import java.nio.file.Paths;
// import java.util.List;

// /**
// * Test program for PdfTextExtractorService with JSON and CSV output
// */
// public class PdfTextExtractorTest {

// public static void main(String[] args) {
// // Check if file path is provided as argument
// if (args.length == 0) {
// System.out.println("Usage: java PdfTextExtractorTest <path-to-pdf-file>
// [output-format]");
// System.out.println("Output formats: json, csv, both (default: both)");
// System.out.println("Example: java PdfTextExtractorTest /path/to/document.pdf
// json");
// return;
// }

// String pdfFilePath = args[0];
// String outputFormat = args.length > 1 ? args[1].toLowerCase() : "both";

// PdfTextExtractorService extractor = new PdfTextExtractorService();

// try {
// System.out.println("Testing PDF Text Extractor with file: " + pdfFilePath);
// System.out.println("Output format: " + outputFormat);
// System.out.println("=" + "=".repeat(60));

// // Extract text from PDF
// List<String> pages = extractTextFromFile(extractor, pdfFilePath);

// // Generate output files
// String baseName = getBaseFileName(pdfFilePath);

// if ("json".equals(outputFormat) || "both".equals(outputFormat)) {
// generateJsonOutput(pages, baseName + "_extracted.json", pdfFilePath);
// }

// if ("csv".equals(outputFormat) || "both".equals(outputFormat)) {
// generateCsvOutput(pages, baseName + "_extracted.csv", pdfFilePath);
// }

// // Display summary
// displaySummary(pages, baseName, outputFormat);

// } catch (Exception e) {
// System.err.println("Error during PDF processing: " + e.getMessage());
// e.printStackTrace();
// }
// }

// /**
// * Extract text from PDF file using the service
// */
// private static List<String> extractTextFromFile(PdfTextExtractorService
// extractor, String filePath)
// throws IOException {

// try (InputStream inputStream = new FileInputStream(filePath)) {
// long startTime = System.currentTimeMillis();
// List<String> pages = extractor.extractTextByPage(inputStream);
// long endTime = System.currentTimeMillis();

// System.out.println("Extraction completed in " + (endTime - startTime) + "
// ms");
// System.out.println("Total pages processed: " + pages.size());
// System.out.println();

// return pages;
// }
// }

// /**
// * Generate JSON output file
// */
// private static void generateJsonOutput(List<String> pages, String outputFile,
// String sourceFile)
// throws IOException {

// try (FileWriter writer = new FileWriter(outputFile)) {
// writer.write("{\n");
// writer.write(" \"source_file\": \"" + escapeJson(sourceFile) + "\",\n");
// writer.write(" \"extraction_timestamp\": \"" +
// java.time.Instant.now().toString() + "\",\n");
// writer.write(" \"total_pages\": " + pages.size() + ",\n");
// writer.write(" \"pages\": [\n");

// for (int i = 0; i < pages.size(); i++) {
// String pageText = pages.get(i);
// writer.write(" {\n");
// writer.write(" \"page_number\": " + (i + 1) + ",\n");
// writer.write(" \"character_count\": " + pageText.length() + ",\n");
// writer.write(" \"word_count\": " + countWords(pageText) + ",\n");
// writer.write(" \"text\": \"" + escapeJson(pageText) + "\"\n");
// writer.write(" }");

// if (i < pages.size() - 1) {
// writer.write(",");
// }
// writer.write("\n");
// }

// writer.write(" ],\n");
// writer.write(" \"summary\": {\n");
// writer.write(" \"total_characters\": " +
// pages.stream().mapToInt(String::length).sum() + ",\n");
// writer.write(" \"total_words\": " +
// pages.stream().mapToInt(PdfTextExtractorTest::countWords).sum() + ",\n");
// writer.write(" \"average_characters_per_page\": " + (pages.size() > 0 ?
// pages.stream().mapToInt(String::length).sum() / pages.size() : 0) + ",\n");
// writer.write(" \"average_words_per_page\": " + (pages.size() > 0 ?
// pages.stream().mapToInt(PdfTextExtractorTest::countWords).sum() /
// pages.size() : 0) + "\n");
// writer.write(" }\n");
// writer.write("}\n");
// }

// System.out.println("JSON output saved to: " + outputFile);
// }

// /**
// * Generate CSV output file
// */
// private static void generateCsvOutput(List<String> pages, String outputFile,
// String sourceFile)
// throws IOException {

// try (FileWriter writer = new FileWriter(outputFile)) {
// // Write CSV header
// writer.write("page_number,character_count,word_count,text\n");

// // Write data rows
// for (int i = 0; i < pages.size(); i++) {
// String pageText = pages.get(i);
// writer.write((i + 1) + ",");
// writer.write(pageText.length() + ",");
// writer.write(countWords(pageText) + ",");
// writer.write("\"" + escapeCsv(pageText) + "\"\n");
// }
// }

// System.out.println("CSV output saved to: " + outputFile);
// }

// /**
// * Display summary information
// */
// private static void displaySummary(List<String> pages, String baseName,
// String outputFormat) {
// System.out.println("\nSUMMARY:");
// System.out.println("-".repeat(40));
// System.out.println("Total pages: " + pages.size());

// int totalChars = pages.stream().mapToInt(String::length).sum();
// int totalWords =
// pages.stream().mapToInt(PdfTextExtractorTest::countWords).sum();

// System.out.println("Total characters: " + totalChars);
// System.out.println("Total words: " + totalWords);
// System.out.println("Average characters per page: " + (pages.size() > 0 ?
// totalChars / pages.size() : 0));
// System.out.println("Average words per page: " + (pages.size() > 0 ?
// totalWords / pages.size() : 0));

// System.out.println("\nOutput files generated:");
// if ("json".equals(outputFormat) || "both".equals(outputFormat)) {
// System.out.println("- " + baseName + "_extracted.json");
// }
// if ("csv".equals(outputFormat) || "both".equals(outputFormat)) {
// System.out.println("- " + baseName + "_extracted.csv");
// }

// System.out.println("\nPage-by-page preview:");
// for (int i = 0; i < Math.min(pages.size(), 3); i++) {
// String preview = pages.get(i).length() > 100 ?
// pages.get(i).substring(0, 100) + "..." : pages.get(i);
// System.out.println("Page " + (i + 1) + ": " + preview.replaceAll("\\s+", "
// ").trim());
// }
// if (pages.size() > 3) {
// System.out.println("... and " + (pages.size() - 3) + " more pages");
// }
// }

// /**
// * Get base filename without extension and path
// */
// private static String getBaseFileName(String filePath) {
// String fileName = Paths.get(filePath).getFileName().toString();
// int lastDot = fileName.lastIndexOf('.');
// return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
// }

// /**
// * Escape string for JSON format
// */
// private static String escapeJson(String text) {
// if (text == null) return "";
// return text.replace("\\", "\\\\")
// .replace("\"", "\\\"")
// .replace("\n", "\\n")
// .replace("\r", "\\r")
// .replace("\t", "\\t");
// }

// /**
// * Escape string for CSV format
// */
// private static String escapeCsv(String text) {
// if (text == null) return "";
// return text.replace("\"", "\"\"")
// .replace("\n", " ")
// .replace("\r", " ");
// }

// /**
// * Simple word counting utility
// */
// private static int countWords(String text) {
// if (text == null || text.trim().isEmpty()) {
// return 0;
// }
// return text.trim().split("\\s+").length;
// }
// }