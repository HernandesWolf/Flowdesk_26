package com.flowdesk.attachment;

import com.flowdesk.common.exception.NotFoundException;
import com.flowdesk.config.FlowDeskProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);

    private final Path root;

    public LocalFileStorage(FlowDeskProperties props) {
        this.root = Path.of(props.storage().path()).toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID companyId, UUID orderId, MultipartFile file) {
        // The stored name is generated, never derived from the client's file name.
        String key = companyId + "/" + orderId + "/" + UUID.randomUUID();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded file", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isReadable(path)) {
            throw new NotFoundException("File content not found");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}", key, e);
        }
    }

    /** Resolves a key under the storage root and refuses anything that would escape it. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
