package com.atsmatcher.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts plain text from an uploaded resume file.
 *
 * Tika is used as the primary extractor since it auto-detects file type
 * (PDF, DOCX, DOC, RTF, even plain text) and handles all of them through
 * one interface, useful since resumes arrive in whatever format the
 * candidate happens to have saved.
 *
 * PDFBox is kept as a PDF-specific fallback: Tika occasionally struggles
 * with PDFs that have unusual encoding or embedded fonts, PDFBox's
 * PDFTextStripper is more forgiving for straightforward text-based PDFs
 * and is worth trying if Tika returns suspiciously little text.
 */
@Service
public class ResumeTextExtractionService {

    private static final int MIN_EXPECTED_CHARS = 50;

    public ExtractionResult extractText(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded resume is empty.");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String lowerName = filename.toLowerCase();

        String text = extractWithTika(file);

        // If Tika returned very little, and this is a PDF, retry with
        // PDFBox directly, some PDFs parse better one way than the other.
        if (text.trim().length() < MIN_EXPECTED_CHARS && lowerName.endsWith(".pdf")) {
            try {
                String pdfBoxText = extractWithPdfBox(file);
                if (pdfBoxText.trim().length() > text.trim().length()) {
                    text = pdfBoxText;
                }
            } catch (Exception ignored) {
                // Keep Tika's result so a malformed or scanned PDF still uploads
                // and can be reported as low-confidence instead of returning 500.
            }
        }

        boolean lowConfidence = text.trim().length() < MIN_EXPECTED_CHARS;

        return new ExtractionResult(text, lowConfidence, filename);
    }

    private String extractWithTika(MultipartFile file) throws IOException {
        try (InputStream stream = file.getInputStream()) {
            BodyContentHandler handler = new BodyContentHandler(-1); // no character limit
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            parser.parse(stream, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (Exception e) {
            // Extraction failure isn't necessarily fatal, PDFBox fallback
            // may still succeed for PDFs, so don't throw here, return
            // empty and let the caller's low-confidence check react.
            return "";
        }
    }

    private String extractWithPdfBox(MultipartFile file) throws IOException {
        try (InputStream stream = file.getInputStream();
             PDDocument document = PDDocument.load(stream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * lowConfidence signals to the frontend that extraction may have
     * failed silently, common with scanned/image-based PDFs (no OCR here)
     * or resumes built as design files exported to PDF with text as
     * outlines. The frontend should show a warning and let the user
     * paste text manually as a fallback rather than silently scoring an
     * empty resume.
     */
    public record ExtractionResult(String text, boolean lowConfidence, String originalFilename) {}
}
