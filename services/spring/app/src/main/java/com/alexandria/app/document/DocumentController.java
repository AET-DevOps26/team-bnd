package com.alexandria.app.document;

import com.alexandria.app.exception.UserNotFoundException;
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
@RequestMapping(path = "/api/v1/documents", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Documents", description = "Document management")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {
    private final DocumentService documentService;
    private final UserService userService;

    private User getCurrentUser(Principal principal) {
        String oidcSubject = principal.getName();
        return userService.findByOidcSubject(oidcSubject)
                .orElseThrow(() -> new UserNotFoundException(oidcSubject));
    }

    public DocumentController(DocumentService documentService, UserService userService) {
        this.documentService = documentService;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List documents of user")
    @ApiResponse(responseCode = "200", description = "Documents retrieved")
    public ResponseEntity<List<Document>> listDocuments(Principal principal) {
        User user = getCurrentUser(principal);
        List<Document> documents = documentService.findByOwnerId(user.getId());
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    @ApiResponse(responseCode = "200", description = "Document retrieved")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Document> getDocument(@PathVariable UUID id, Principal principal) {
        User user = getCurrentUser(principal);
        Document document = documentService.findById(id);

        if (!document.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(document);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document")
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(responseCode = "403", description = "Cannot delete other users documents")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id, Principal principal) {
        User user = getCurrentUser(principal);
        documentService.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
