package com.example.demo.api;

import com.example.demo.models.ResumeDocument;
import com.example.demo.repository.ResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);
    private final ResumeRepository resumeRepository;

    public ResumeController(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> upload(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("resume upload rejected: empty file");
            return ResponseEntity.badRequest().body("file must not be empty");
        }

        String contentType = file.getContentType();
        log.info("resume upload received: name={} size={} contentType={}",
                file.getOriginalFilename(), file.getSize(), contentType);
        String content;
        try {
            content = extractContent(file, contentType);
        } catch (IOException e) {
            log.error("resume upload failed to read file", e);
            return ResponseEntity.internalServerError().body("failed to read file");
        } catch (IllegalArgumentException e) {
            log.warn("resume upload rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        ResumeDocument resume = new ResumeDocument(
                file.getOriginalFilename(),
                contentType,
                content,
                LocalDateTime.now()
        );
        resumeRepository.save(resume);
        log.info("resume upload stored");
        return ResponseEntity.ok("resume uploaded");
    }

    private String extractContent(MultipartFile file, String contentType) throws IOException {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            try (InputStream is = file.getInputStream();
                 PDDocument document = Loader.loadPDF(is.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        }

        if (contentType != null && contentType.startsWith("text/")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        throw new IllegalArgumentException("only text/* or application/pdf resume files are supported");
    }
}
