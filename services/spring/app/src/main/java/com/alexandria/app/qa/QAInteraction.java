package com.alexandria.app.qa;

import com.alexandria.app.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "qa_interactions")
public class QAInteraction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @ElementCollection
    @CollectionTable(name = "qa_source_documents", joinColumns = @JoinColumn(name = "qa_id"))
    @Column(name = "document_id")
    private List<String> sourceObjectKeys;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String modelUsed;

    public QAInteraction() {
    }

    public QAInteraction(User user, String question, String answer, List<String> sourceObjectKeys, String modelUsed) {
        this.user = user;
        this.question = question;
        this.answer = answer;
        this.sourceObjectKeys = sourceObjectKeys;
        this.modelUsed = modelUsed;
        this.timestamp = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getSourceObjectKeys() {
        return sourceObjectKeys;
    }

    public void setSourceObjectKeys(List<String> sourceObjectKeys) {
        this.sourceObjectKeys = sourceObjectKeys;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }
}
