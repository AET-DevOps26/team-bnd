package com.alexandria.qa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/qa", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "QA Service", description = "Question answering over the user's knowledge base")
@SecurityRequirement(name = "bearerAuth")
public class QAController {

    private final QAService qaService;

    public QAController(QAService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question about your documents")
    @ApiResponse(responseCode = "200", description = "Answer generated")
    public ResponseEntity<QAInteraction> ask(@Valid @RequestBody AskRequest request, Principal principal) {
        return ResponseEntity.ok(qaService.ask(principal.getName(), request.question()));
    }

    @GetMapping("/history")
    @Operation(summary = "Get Q&A history")
    @ApiResponse(responseCode = "200", description = "Q&A history retrieved")
    public ResponseEntity<List<QAInteraction>> getHistory(Principal principal) {
        return ResponseEntity.ok(qaService.getHistory(principal.getName()));
    }

    @DeleteMapping("/history")
    @Operation(summary = "Delete Q&A history")
    @ApiResponse(responseCode = "204", description = "Q&A history deleted")
    public ResponseEntity<Void> deleteHistory(Principal principal) {
        qaService.deleteHistory(principal.getName());
        return ResponseEntity.noContent().build();
    }

    public record AskRequest(@NotBlank @Size(min = 1, max = 1500) String question) {
    }
}
