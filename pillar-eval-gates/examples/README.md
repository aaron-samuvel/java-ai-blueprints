# Examples

## Gate any module's answers
```java
EvaluationGate gate = new EvaluationGate(new LexicalJudgeChatModel());   // or an Ollama ChatModel
GateReport report = gate.run(EvaluationGate.load("golden-set.json"));
assertThat(report.passes(0.9, 0.9)).as(report.summary()).isTrue();
```

## Evaluate one answer
```java
EvalCase c = new EvalCase("refund", "How long for a refund?",
        List.of("Customers can request a full refund within 30 days of delivery."),
        "You can request a full refund within 90 days.");
gate.facts(c).isPass();   // false: "90" isn't supported by the document
```

## Golden-set format (`src/test/resources/*.json`)
```json
[{"id":"refund-window","question":"…","context":["…"],"answer":"…"}]
```
