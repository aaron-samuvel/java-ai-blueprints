# Examples

## REST walkthrough
```bash
mvn -pl blueprint-02-langgraph4j-hitl-evaluation -am spring-boot:run
# start: the graph runs drafter -> evaluator and pauses before human_review
curl -s -X POST localhost:8082/api/reviews -H 'Content-Type: application/json' \
  -d '{"question":"How much annual leave?","context":"Employees get 24 days of paid annual leave per calendar year."}'
# -> {"threadId":"…","status":"AWAITING_HUMAN","draft":"…","history":[…]}
curl -s -X POST localhost:8082/api/reviews/<threadId>/decision -H 'Content-Type: application/json' \
  -d '{"approve":false,"comment":"Say it is per calendar year"}'
curl -s -X POST localhost:8082/api/reviews/<threadId>/decision -H 'Content-Type: application/json' \
  -d '{"approve":true}'
# -> status PUBLISHED
```
`ReviewApiTest` checks this flow (start, then approve) over HTTP.

## Embed the graph
```java
CompiledGraph<ReviewState> graph = ReviewGraph.build(chatModel,
        new EvaluationGate(judgeModel), new MemorySaver(), 2);
ReviewWorkflow wf = new ReviewWorkflow(graph);
ReviewView v = wf.start(question, context);
wf.decide(v.threadId(), true, null);
```
