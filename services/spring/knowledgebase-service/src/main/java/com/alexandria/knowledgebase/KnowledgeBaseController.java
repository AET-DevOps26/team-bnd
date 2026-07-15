package com.alexandria.knowledgebase;

import com.alexandria.knowledgebase.document.Document;
import com.alexandria.knowledgebase.document.ExtractedEntity;
import com.alexandria.knowledgebase.document.Summary;
import com.alexandria.knowledgebase.document.TagSource;
import com.alexandria.knowledgebase.dto.*;
import com.alexandria.knowledgebase.search.SearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public REST API for document management and knowledge operations.
 *
 * <p>All endpoints are scoped to the authenticated caller; the OIDC subject from the
 * bearer token is used as the document owner, so a user only ever sees their own
 * documents. Uploads and creates trigger an asynchronous GenAI pipeline (summary,
 * entities, tags, indexing) in the service layer, the reprocess endpoints re-run
 * individual steps on demand.
 */
@RestController
@RequestMapping(path = "/api/v1/knowledgebase", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "KnowledgeBase Service", description = "Document management and AI-powered knowledge operations")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/documents")
    @Operation(summary = "List all documents")
    @ApiResponse(responseCode = "200", description = "Documents retrieved")
    public ResponseEntity<List<Document>> listDocuments(Principal principal) {
        return ResponseEntity.ok(knowledgeBaseService.getDocuments(principal.getName()));
    }

    @PostMapping(value = "/documents/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new document with text content")
    @ApiResponse(responseCode = "201", description = "Document created")
    public ResponseEntity<Document> createDocument(
                                                   @RequestBody CreateDocumentRequest request, Principal principal) {
        Document document = knowledgeBaseService.createDocument(
                principal.getName(), request.fileName(), request.objectKey(), request.fileType(), request.fileSize(), request.textContent());
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document file")
    @ApiResponse(responseCode = "201", description = "Document uploaded")
    @ApiResponse(responseCode = "400", description = "Invalid file")
    public ResponseEntity<Document> uploadDocument(
                                                   @RequestParam("file") MultipartFile file, Principal principal) {
        Document document = knowledgeBaseService.uploadDocument(principal.getName(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "Get document by ID")
    @ApiResponse(responseCode = "200", description = "Document retrieved")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Document> getDocument(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(knowledgeBaseService.getDocument(id, principal.getName()));
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Delete document")
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Principal principal) {
        knowledgeBaseService.deleteDocument(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/documents/{id}")
    @Operation(summary = "Update document metadata")
    @ApiResponse(responseCode = "200", description = "Document updated")
    @ApiResponse(responseCode = "400", description = "Bad Request")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Document> updateDocument(
                                                   @PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request, Principal principal) {
        Document updatedDocument = knowledgeBaseService.updateDocument(id, request, principal.getName());
        return ResponseEntity.ok(updatedDocument);
    }

    @GetMapping("/documents/{id}/download")
    @Operation(summary = "Download document file")
    @ApiResponse(responseCode = "200", description = "File content")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id, Principal principal) {
        Document document = knowledgeBaseService.getDocument(id, principal.getName());

        return knowledgeBaseService.getFileContent(id, principal.getName()).map(bytes -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(document.getFileType()));
            headers.setContentDispositionFormData("attachment", document.getFileName());
            headers.setContentLength(bytes.length);
            return ResponseEntity.ok().headers(headers).body(bytes);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search/text")
    @Operation(summary = "Keyword search over document filenames and content")
    @ApiResponse(responseCode = "200", description = "Search results")
    public ResponseEntity<TextSearchResponseDto> searchText(@RequestParam String q, Principal principal) {
        return ResponseEntity.ok(new TextSearchResponseDto(knowledgeBaseService.search(principal.getName(), q)));
    }

    /**
     * Ranks the user's indexed documents by semantic similarity to the query.
     * Falls back to keyword search when the GenAI call fails or the index is empty,
     * so a result set is always returned.
     *
     * @param limit maximum number of hits to return, between 1 and 50
     */
    @GetMapping("/search/semantic")
    @Operation(summary = "Semantic search over document content, with keyword fallback")
    @ApiResponse(responseCode = "200", description = "Ranked search results with match context")
    @ApiResponse(responseCode = "400", description = "Invalid limit")
    public ResponseEntity<SemanticSearchResponseDto> searchSemantic(
                                                                    @RequestParam String q, @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit, Principal principal) {
        return ResponseEntity.ok(knowledgeBaseService.semanticSearch(principal.getName(), q, limit));
    }

    @PostMapping("/documents/{id}/tags")
    @Operation(summary = "Add tag to document")
    @ApiResponse(responseCode = "204", description = "Tag added")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> addTag(
                                       @PathVariable UUID id, @RequestBody AddTagRequest request, Principal principal) {
        knowledgeBaseService.addTag(id, principal.getName(), request.label(), TagSource.USER);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documents/{documentId}/tags/{tagId}")
    @Operation(summary = "Remove tag from document")
    @ApiResponse(responseCode = "204", description = "Tag removed")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> removeTag(
                                          @PathVariable UUID documentId, @PathVariable UUID tagId, Principal principal) {
        knowledgeBaseService.removeTag(documentId, principal.getName(), tagId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the GenAI-generated summary for a document, or 204 No Content while it is
     * still being processed (summaries are produced asynchronously after upload).
     */
    @GetMapping("/documents/{id}/summary")
    @Operation(summary = "Get document summary")
    @ApiResponse(responseCode = "200", description = "Document summary retrieved")
    @ApiResponse(responseCode = "204", description = "Summary not yet available", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<DocumentSummaryDto> getSummary(@PathVariable UUID id, Principal principal) {
        Summary summary = knowledgeBaseService.getDocumentSummary(id, principal.getName());
        if (summary == null) {
            return ResponseEntity.noContent().build();
        }
        DocumentSummaryDto summaryDto = new DocumentSummaryDto(
                summary.getContent(), summary.getModelUsed(), summary.getGeneratedAt());
        return ResponseEntity.ok(summaryDto);
    }

    @GetMapping("/documents/{id}/entities")
    @Operation(summary = "Get document entities")
    @ApiResponse(responseCode = "200", description = "Document entities retrieved")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<DocumentEntityResponseDto> getEntities(
                                                                 @PathVariable UUID id, Principal principal) {
        List<ExtractedEntity> extractedEntities = knowledgeBaseService.getDocumentEntities(id, principal.getName());
        List<DocumentEntityDto> entityList = extractedEntities.stream().map(
                entity -> new DocumentEntityDto(
                        entity.getName(), entity.getType(), entity.getConfidence())).toList();

        DocumentEntityResponseDto documentEntityResponseDto = new DocumentEntityResponseDto(id, entityList);
        return ResponseEntity.ok(documentEntityResponseDto);
    }

    @PostMapping("/documents/{id}/reprocess/summary")
    @Operation(summary = "Reprocess document summary")
    @ApiResponse(responseCode = "204", description = "Document summary reprocessed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> reprocessSummary(@PathVariable UUID id, Principal principal) {
        knowledgeBaseService.reprocessSummary(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{id}/reprocess/entities")
    @Operation(summary = "Reprocess document entities")
    @ApiResponse(responseCode = "204", description = "Document entities reprocessed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> reprocessEntities(@PathVariable UUID id, Principal principal) {
        knowledgeBaseService.reprocessEntities(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/documents/{id}/reprocess/tags")
    @Operation(summary = "Reprocess document tags")
    @ApiResponse(responseCode = "204", description = "Document tags reprocessed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> reprocessTags(@PathVariable UUID id, Principal principal) {
        knowledgeBaseService.reprocessTags(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/history/search")
    @Operation(summary = "Get search history")
    @ApiResponse(responseCode = "200", description = "Search history retrieved")
    public ResponseEntity<List<SearchQuery>> getSearchHistory(Principal principal) {
        return ResponseEntity.ok(knowledgeBaseService.getSearchHistory(principal.getName()));
    }

    @DeleteMapping("/history/search")
    @Operation(summary = "Delete search history")
    @ApiResponse(responseCode = "204", description = "Search history deleted")
    public ResponseEntity<Void> deleteSearchHistory(Principal principal) {
        knowledgeBaseService.deleteSearchHistory(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tags")
    @Operation(summary = "Get all unique tags for user")
    @ApiResponse(responseCode = "200", description = "Tags retrieved successfully")
    public ResponseEntity<TagListDto> getTags(Principal principal) {
        Map<String, Long> tagsWithCount = knowledgeBaseService.getTagsForUserWithCount(principal.getName());

        List<TagDto> tagDtos = tagsWithCount.entrySet().stream().map(entry -> new TagDto(entry.getKey(), entry.getValue())).sorted((a, b) -> Long.compare(b.documentCount(), a.documentCount())).toList();

        return ResponseEntity.ok(new TagListDto(tagDtos));
    }

    public record CreateDocumentRequest(
                                        String fileName, String objectKey, String fileType, Long fileSize,
                                        String textContent) {
    }

    public record AddTagRequest(String label) {
    }
}
