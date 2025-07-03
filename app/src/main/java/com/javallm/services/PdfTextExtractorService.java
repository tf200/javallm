package com.javallm.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfTextExtractorService {

    // Optimal chunk size based on a 512 token limit (512 * ~4 chars/token, with a
    // buffer)
    private static final int CHUNK_SIZE = 800;
    // Overlap to maintain context between chunks
    private static final int CHUNK_OVERLAP = 200;

    /**
     * A record to hold a chunk of text and its corresponding page number(s).
     */
    public record TextChunk(String content, String pageLabel) {
    }

    /**
     * A private record to temporarily hold page content and its number.
     */
    private record PageContent(String text, int pageNumber) {
    }

    /**
     * Extracts text from a PDF, then splits it into overlapping chunks.
     *
     * @param inputStream The InputStream of the PDF file.
     * @return A List of TextChunks, each containing a piece of text and its page
     *         metadata.
     * @throws IOException if the PDF cannot be loaded or read.
     */
    public List<TextChunk> extractAndSplitText(InputStream inputStream) throws IOException {
        List<PageContent> pageContents = new ArrayList<>();
        // 1. Extract text from each page to retain page boundary information.
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    pageContents.add(new PageContent(text, i));
                }
            }
        }
        // 2. Split the collected text into chunks.
        return splitTextIntoChunks(pageContents);
    }

    /**
     * Splits the text content from all pages into manageable chunks using a sliding
     * window.
     */
    private List<TextChunk> splitTextIntoChunks(List<PageContent> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder fullTextBuilder = new StringBuilder();
        List<Integer> pageStartIndices = new ArrayList<>();

        // Concatenate all page text and record the starting character index of each
        // page.
        for (PageContent page : pages) {
            pageStartIndices.add(fullTextBuilder.length());
            fullTextBuilder.append(page.text()).append("\n\n"); // Add separator for clarity
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
                String pageLabel = getPageLabelForChunk(start, end, pageStartIndices, pages.size());
                chunks.add(new TextChunk(chunkText, pageLabel));
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
     * Determines the page range (e.g., "1", "2-3") for a given text chunk.
     */
    private String getPageLabelForChunk(int chunkStart, int chunkEnd, List<Integer> pageStartIndices, int totalPages) {
        int startPage = -1;
        int endPage = -1;

        for (int i = 0; i < pageStartIndices.size(); i++) {
            int pageStartIndex = pageStartIndices.get(i);
            int nextPageStartIndex = (i + 1 < pageStartIndices.size()) ? pageStartIndices.get(i + 1)
                    : Integer.MAX_VALUE;

            // Check if the chunk overlaps with the current page's text span.
            if (chunkStart < nextPageStartIndex && chunkEnd > pageStartIndex) {
                if (startPage == -1) {
                    startPage = i + 1;
                }
                endPage = i + 1;
            }
        }

        if (startPage == -1)
            return "N/A";
        if (startPage == endPage)
            return String.valueOf(startPage);
        return String.format("%d-%d", startPage, endPage);
    }
}