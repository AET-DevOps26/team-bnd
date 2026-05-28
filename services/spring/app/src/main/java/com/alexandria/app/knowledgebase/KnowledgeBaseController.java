package com.alexandria.app.knowledgebase;

import com.alexandria.app.document.Document;
import com.alexandria.app.document.TagSource;
import com.alexandria.app.exception.UserNotFoundException;
import com.alexandria.app.qa.QAInteraction;
import com.alexandria.app.search.SearchQuery;
import com.alexandria.app.user.User;
import com.alexandria.app.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/knowledgebase", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "KnowledgeBase", description = "Document management and AI-powered knowledge operations")
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final UserService userService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, UserService userService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.userService = userService;
    }

    @GetMapping("/documents")
    @Operation(summary = "List all documents")
    @ApiResponse(responseCode = "200", description = "Documents retrieved")
    public ResponseEntity<List<Document>> listDocuments(Principal principal) {
        User user = getCurrentUser(principal);
        return ResponseEntity.ok(knowledgeBaseService.getDocuments(user.getId()));
    }

    @PostMapping(value = "/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new document")
    @ApiResponse(responseCode = "201", description = "Document created")
    public ResponseEntity<Document> createDocument(@RequestBody CreateDocumentRequest request, Principal principal) {
        User user = getCurrentUser(principal);
        Document document = knowledgeBaseService.createDocument(
                user,
                request.fileName(),
                request.filePath(),
                request.fileType(),
                request.fileSize(),
                request.textContent()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document by ID")
    @ApiResponse(responseCode = "200", description = "Document retrieved")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Document> getDocument(@PathVariable UUID id, Principal principal) {
        User user = getCurrentUser(principal);
        return ResponseEntity.ok(knowledgeBaseService.getDocument(id, user.getId()));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete document")
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Principal principal) {
        User user = getCurrentUser(principal);
        knowledgeBaseService.deleteDocument(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search documents")
    @ApiResponse(responseCode = "200", description = "Search results")
    public ResponseEntity<List<Document>> search(@RequestParam String q, Principal principal) {
        User user = getCurrentUser(principal);
        return ResponseEntity.ok(knowledgeBaseService.search(user, q));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question about your documents")
    @ApiResponse(responseCode = "200", description = "Answer generated")
    public ResponseEntity<QAInteraction> ask(@RequestBody AskRequest request, Principal principal) {
        User user = getCurrentUser(principal);
        return ResponseEntity.ok(knowledgeBaseService.ask(user, request.question()));
    }

    @PostMapping("/documents/{id}/tags")
    @Operation(summary = "Add tag to document")
    @ApiResponse(responseCode = "204", description = "Tag added")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> addTag(@PathVariable UUID id, @RequestBody AddTagRequest request, Principal principal) {
        User user = getCurrentUser(principal);
        knowledgeBaseService.addTag(id, user.getId(), request.label(), TagSource.USER);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documents/{documentId}/tags/{tagId}")
    @Operation(summary = "Remove tag from document")
    @ApiResponse(responseCode = "204", description = "Tag removed")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> removeTag(@PathVariable UUID documentId, @PathVariable UUID tagId, Principal principal) {
        User user = getCurrentUser(principal);
        knowledgeBaseService.removeTag(documentId, user.getId(), tagId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history/qa")
    @Operation(summary = "Get Q&A history")
    @ApiResponse(responseCode = "200", description = "Q&A history retrieved")
    public ResponseEntity<List<QAInteraction>> getQAHistory(Principal principal) {
        User user = getCurrentUser(principal);
        return ResponseEntity.ok(knowledgeBaseService.getQAHistory(user.getId()));
    }

    @GetMapping("/history/search")
    @Operation(summary = "Get search history")
    @ApiResponse(responseCode = "200", description = "Search history retrieved")
    public ResponseEntity<List<SearchQuery>> getSearchHistory(Principal principal) {
        User user = getCurrentUser(principal);
        return ResponseEntity.ok(knowledgeBaseService.getSearchHistory(user.getId()));
    }

    private User getCurrentUser(Principal principal) {
        String oidcSubject = principal.getName();
        return userService.findByOidcSubject(oidcSubject)
                .orElseThrow(() -> new UserNotFoundException(oidcSubject));
    }

    public record CreateDocumentRequest(String fileName, String filePath, String fileType, Long fileSize, String textContent) {}

    public record AskRequest(String question) {}

    public record AddTagRequest(String label) {}
}
