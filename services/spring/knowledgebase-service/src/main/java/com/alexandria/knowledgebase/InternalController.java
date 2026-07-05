package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.Document;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/internal/knowledgebase", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "KnowledgeBase Internal", description = "Service-to-service endpoints, reachable only inside the cluster network")
public class InternalController {

    private final KnowledgeBaseService knowledgeBaseService;

    public InternalController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @DeleteMapping("/users/{subject}")
    @Operation(operationId = "kbInternalDeleteUserData", summary = "Purge all knowledgebase data for a user (internal)")
    @ApiResponse(responseCode = "204", description = "User data purged")
    public ResponseEntity<Void> deleteUserData(@PathVariable("subject") String subject) {
        knowledgeBaseService.deleteAllForUser(subject);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{subject}/document-keys")
    @Operation(summary = "List object keys for a user's documents (internal)")
    @ApiResponse(responseCode = "200", description = "Object keys returned")
    public ResponseEntity<List<String>> listDocumentKeys(@PathVariable("subject") String subject) {
        return ResponseEntity.ok(
                knowledgeBaseService.getDocuments(subject).stream()
                        .map(Document::getObjectKey)
                        .toList());
    }
}
