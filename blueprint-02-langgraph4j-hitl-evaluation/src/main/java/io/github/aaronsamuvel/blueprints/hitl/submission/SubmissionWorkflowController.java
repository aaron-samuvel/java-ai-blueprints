package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The handbook's workflow API: start, inspect, resume. */
@RestController
@RequestMapping("/api/v1/workflows")
public class SubmissionWorkflowController {

    public record StartRequest(String submissionId, String documentText) {}

    public record ResumeRequest(boolean approved, String reviewer, String comment) {}

    private final SubmissionWorkflowService service;

    public SubmissionWorkflowController(SubmissionWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public SubmissionWorkflowService.WorkflowView start(@RequestBody StartRequest request) {
        try {
            return service.start(request.submissionId(), request.documentText());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{threadId}/state")
    public SubmissionWorkflowService.WorkflowView state(@PathVariable String threadId) {
        try {
            return service.state(threadId);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{threadId}/resume")
    public SubmissionWorkflowService.WorkflowView resume(@PathVariable String threadId, @RequestBody ResumeRequest request) throws Exception {
        try {
            return service.resume(threadId, new HumanVerdict(request.approved(),
                    request.reviewer() == null ? "anonymous" : request.reviewer(), request.comment() == null ? "" : request.comment()));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
