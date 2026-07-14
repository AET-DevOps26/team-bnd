package com.alexandria.qa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalControllerTest {

    @Mock
    private QAService qaService;

    @InjectMocks
    private InternalController controller;

    @Test
    void unit_qa_internalDeleteUserDataDelegates() {
        ResponseEntity<Void> response = controller.deleteUserData("owner");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(qaService).deleteAllForUser("owner");
    }
}
