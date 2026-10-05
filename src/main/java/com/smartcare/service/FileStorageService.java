package com.smartcare.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stores prescription images/PDFs on disk under random names (never the user-supplied name). */
@Service
public class FileStorageService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "pdf");
    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_BYTES = 5L * 1024 * 1024;   // 5 MB
    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** Prescriptions: JPG, PNG or PDF. */
    public String store(MultipartFile file) {
        return store(file, ALLOWED, "Please choose a prescription file.", "Only JPG, PNG or PDF files are allowed.");
    }

    /** Medicine photos: images only. */
    public String storeImage(MultipartFile file) {
        return store(file, IMAGES, "Please choose a photo.", "Photos must be JPG, PNG or WEBP.");
    }

    private String store(MultipartFile file, Set<String> allowed, String emptyMsg, String typeMsg) {
        if (file == null || file.isEmpty()) throw new IllegalStateException(emptyMsg);
        if (file.getSize() > MAX_BYTES)
            throw new IllegalStateException("File is too large. The maximum size is 5 MB.");
        String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "file"));
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!allowed.contains(ext)) throw new IllegalStateException(typeMsg);
        // An extension is only a label: anything can be renamed to .jpg. Read the first
        // bytes and confirm the file really is the type it claims, so an executable or
        // script cannot be stored (and later served) as if it were a prescription image.
        if (!contentMatchesExtension(file, ext))
            throw new IllegalStateException("That file's contents do not match its ." + ext
                    + " extension. Please upload a real " + ext.toUpperCase() + " file.");
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.copy(file.getInputStream(), root.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the file. Please try again.");
        }
        return name;
    }

    /** Checks the file's magic bytes (its real signature) against the claimed extension. */
    private boolean contentMatchesExtension(MultipartFile file, String ext) {
        byte[] head = new byte[12];
        int read;
        try (var in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        if (read < 4) return false;

        switch (ext) {
            case "jpg":
            case "jpeg":
                // JPEG always starts FF D8 FF
                return (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png":
                // PNG: 89 50 4E 47 0D 0A 1A 0A
                return read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                        && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                        && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A;
            case "pdf":
                // PDF: "%PDF"
                return head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
            case "webp":
                // WEBP: "RIFF" .... "WEBP"
                return read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default:
                return false;
        }
    }

    public Resource load(String name) {
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) throw new IllegalStateException("File not found.");
        return new FileSystemResource(p);
    }

    /**
     * Removes a stored file. Used when a customer withdraws a prescription, so the
     * image does not stay on disk after its database row is gone. A missing file is
     * not an error: the row is being deleted either way.
     */
    public void delete(String name) {
        if (name == null || name.isBlank()) return;
        try {
            Files.deleteIfExists(root.resolve(name).normalize());
        } catch (IOException e) {
            // Non-fatal: the database row is still removed. Left deliberately quiet
            // so a locked or already-removed file cannot block the user's action.
        }
    }
}
