package com.example.aip.platform.console;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.example.aip.platform.domain.Document;
import com.example.aip.platform.domain.DocumentStatus;
import com.example.aip.platform.repo.DocumentRepository;
import com.example.aip.platform.runtime.RagServiceClient;
import com.example.aip.platform.runtime.RuntimeUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 知识库文档 / 检索：数据在控制面，索引与检索转发 rag-service。 */
@RestController
public class ConsoleDatasetController {

    private final DocumentRepository docRepo;
    private final RagServiceClient rag;

    public ConsoleDatasetController(DocumentRepository docRepo, RagServiceClient rag) {
        this.docRepo = docRepo;
        this.rag = rag;
    }

    @PostMapping("/api/datasets/{datasetId}/documents")
    public ResponseEntity<?> createDocument(@PathVariable String datasetId,
                                            @RequestBody CreateDocumentRequest req) {
        String docId = UUID.randomUUID().toString();
        String jobId = UUID.randomUUID().toString();

        Document doc = new Document();
        doc.setId(docId);
        doc.setDatasetId(datasetId);
        doc.setName(req.name());
        // 原型期 content 直接存库，fileKey 约定 document:{id}，inner /files 按此返回
        doc.setContent(req.content());
        doc.setStatus(DocumentStatus.WAITING);
        doc.setJobId(jobId);
        docRepo.save(doc);

        try {
            rag.submitIndexJob(jobId, datasetId, docId);
        } catch (RuntimeUnavailableException e) {
            doc.setStatus(DocumentStatus.ERROR);
            docRepo.save(doc);
            return ResponseEntity.status(502).body(Map.of("error", String.valueOf(e.getMessage())));
        }
        return ResponseEntity.ok(Map.of("documentId", docId, "jobId", jobId));
    }

    @GetMapping("/api/datasets/{datasetId}/documents/{documentId}/indexing-status")
    public ResponseEntity<?> indexingStatus(@PathVariable String datasetId, @PathVariable String documentId) {
        Document doc = docRepo.findById(documentId).orElse(null);
        if (doc == null || !datasetId.equals(doc.getDatasetId())) {
            return ResponseEntity.status(404).body(Map.of("error", "document not found"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", doc.getStatus());
        out.put("segmentCount", doc.getSegmentCount());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/api/datasets/{datasetId}/retrieve")
    public ResponseEntity<?> retrieve(@PathVariable String datasetId, @RequestBody RetrieveRequest req) {
        try {
            JsonNode result = rag.retrieve(datasetId, req.query(), req.topK());
            return ResponseEntity.ok(result);
        } catch (RuntimeUnavailableException e) {
            return ResponseEntity.status(502).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
