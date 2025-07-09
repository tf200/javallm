package com.javallm.services;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class WordTextExtractorService {

    // Optimal chunk size based on a 512 token limit (512 * ~4 chars/token, with a
    // buffer)
    private static final int CHUNK_SIZE = 700;
    // Overlap to maintain context between chunks
    private static final int CHUNK_OVERLAP = 200;

    /**
     * A record to hold a chunk of text and its corresponding page/section
     * information.
     */
    public record TextChunk(String content, String sectionLabel) {
    }

    /**
     * A private record to temporarily hold paragraph content and its index.
     */
    private record ParagraphContent(String text, int paragraphIndex) {
    }

    /**
     * Extracts text from a Word document (.doc or .docx), then splits it into
     * overlapping chunks.
     *
     * @param inputStream The InputStream of the Word document file.
     * @param filename    The filename to determine the document type (.doc or
     *                    .docx).
     * @return A List of TextChunks, each containing a piece of text and its section
     *         metadata.
     * @throws IOException if the document cannot be loaded or read.
     */
    public List<TextChunk> extractAndSplitText(InputStream inputStream, String filename) throws IOException {
        List<ParagraphContent> paragraphContents = new ArrayList<>();

        if (filename.toLowerCase().endsWith(".docx")) {
            paragraphContents = extractFromDocx(inputStream);
        } else if (filename.toLowerCase().endsWith(".doc")) {
            paragraphContents = extractFromDoc(inputStream);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Only .doc and .docx files are supported.");
        }

        return splitTextIntoChunks(paragraphContents);
    }

    /**
     * Extracts text from a .docx file using Apache POI XWPF.
     */
    private List<ParagraphContent> extractFromDocx(InputStream inputStream) throws IOException {
        List<ParagraphContent> paragraphContents = new ArrayList<>();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            // Extract text paragraph by paragraph to maintain structure
            for (int i = 0; i < document.getParagraphs().size(); i++) {
                String paragraphText = document.getParagraphs().get(i).getText();
                if (paragraphText != null && !paragraphText.trim().isEmpty()) {
                    paragraphContents.add(new ParagraphContent(paragraphText.trim(), i + 1));
                }
            }

            // Also extract text from tables
            document.getTables().forEach(table -> {
                table.getRows().forEach(row -> {
                    row.getTableCells().forEach(cell -> {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            paragraphContents.add(new ParagraphContent(cellText.trim(), -1)); // -1 indicates table
                                                                                              // content
                        }
                    });
                });
            });
        }

        return paragraphContents;
    }

    /**
     * Extracts text from a .doc file using Apache POI HWPF.
     */
    private List<ParagraphContent> extractFromDoc(InputStream inputStream) throws IOException {
        List<ParagraphContent> paragraphContents = new ArrayList<>();

        try (HWPFDocument document = new HWPFDocument(inputStream);
                WordExtractor extractor = new WordExtractor(document)) {

            // For .doc files, we extract the full text and split it by paragraphs
            String fullText = extractor.getText();
            if (fullText != null && !fullText.trim().isEmpty()) {
                String[] paragraphs = fullText.split("\\r?\\n");
                for (int i = 0; i < paragraphs.length; i++) {
                    String paragraph = paragraphs[i].trim();
                    if (!paragraph.isEmpty()) {
                        paragraphContents.add(new ParagraphContent(paragraph, i + 1));
                    }
                }
            }
        }

        return paragraphContents;
    }

    /**
     * Splits the text content from all paragraphs into manageable chunks using a
     * sliding window.
     */
    private List<TextChunk> splitTextIntoChunks(List<ParagraphContent> paragraphs) {
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();
        List<Integer> paragraphStartIndices = new ArrayList<>();

        // Concatenate all paragraph text and record the starting character index of
        // each paragraph.
        for (ParagraphContent paragraph : paragraphs) {
            paragraphStartIndices.add(fullTextBuilder.length());
            fullTextBuilder.append(paragraph.text()).append("\n\n"); // Add separator for clarity
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
                String sectionLabel = getSectionLabelForChunk(start, end, paragraphStartIndices, paragraphs.size());
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
     * Determines the paragraph range (e.g., "1", "2-3") for a given text chunk.
     */
    private String getSectionLabelForChunk(int chunkStart, int chunkEnd, List<Integer> paragraphStartIndices,
            int totalParagraphs) {
        int startParagraph = -1;
        int endParagraph = -1;

        for (int i = 0; i < paragraphStartIndices.size(); i++) {
            int paragraphStartIndex = paragraphStartIndices.get(i);
            int nextParagraphStartIndex = (i + 1 < paragraphStartIndices.size()) ? paragraphStartIndices.get(i + 1)
                    : Integer.MAX_VALUE;

            // Check if the chunk overlaps with the current paragraph's text span.
            if (chunkStart < nextParagraphStartIndex && chunkEnd > paragraphStartIndex) {
                if (startParagraph == -1) {
                    startParagraph = i + 1;
                }
                endParagraph = i + 1;
            }
        }

        if (startParagraph == -1)
            return "N/A";
        if (startParagraph == endParagraph)
            return "P" + startParagraph; // P for Paragraph
        return String.format("P%d-%d", startParagraph, endParagraph);
    }
}