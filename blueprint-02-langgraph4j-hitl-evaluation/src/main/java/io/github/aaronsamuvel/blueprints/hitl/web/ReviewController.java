package io.github.aaronsamuvel.blueprints.hitl.web;

import io.github.aaronsamuvel.blueprints.hitl.ReviewWorkflow;
import io.github.aaronsamuvel.blueprints.hitl.ReviewWorkflow.ReviewView;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    public record StartRequest(String question, String context) {}

    public record Decision(boolean approve, String comment) {}

    private final ReviewWorkflow workflow;

    public ReviewController(ReviewWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping
    public ReviewView start(@RequestBody StartRequest req) {
        return workflow.start(req.question(), req.context());
    }

    @GetMapping("/{id}")
    public ReviewView get(@PathVariable String id) {
        return workflow.view(id);
    }

    @PostMapping("/{id}/decision")
    public ReviewView decide(@PathVariable String id, @RequestBody Decision d) throws Exception {
        return workflow.decide(id, d.approve(), d.comment());
    }
}
