package com.alexandria.qa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/qa", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "QA Internal", description = "Service-to-service endpoints, reachable only inside the cluster network")
public class InternalController {

    private final QAService qaService;

    public InternalController(QAService qaService) {
        this.qaService = qaService;
    }

    @DeleteMapping("/users/{subject}")
    @Operation(operationId = "qaInternalDeleteUserData", summary = "Purge all QA data for a user (internal)")
    @ApiResponse(responseCode = "204", description = "User data purged")
    public ResponseEntity<Void> deleteUserData(@PathVariable("subject") String subject) {
        qaService.deleteAllForUser(subject);
        return ResponseEntity.noContent().build();
    }
}
