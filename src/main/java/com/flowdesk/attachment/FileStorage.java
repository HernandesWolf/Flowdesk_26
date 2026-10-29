package com.flowdesk.attachment;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Where attachment bytes live. The default implementation writes to the local disk; swapping to S3 or
 * GCS only means providing another implementation of this interface.
 */
public interface FileStorage {

    /** Stores the file and returns an opaque key used to load or delete it later. */
    String store(UUID companyId, UUID orderId, MultipartFile file);

    Resource load(String key);

    void delete(String key);
}
