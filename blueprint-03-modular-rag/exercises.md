# Exercises: Blueprint 03

*(author's design, not from the handbook)*

1. **New branch.** Add an `HR_FORMS` intent with its own corpus folder. Write the router test first.
2. **LLM fallback router.** When `KeywordQueryRouter` returns `GENERAL`, ask the model to classify the
   query, using a record `Classification(QueryIntent intent, double confidence)`. Stub the classifier in tests.
3. **Query rewriting.** Add Spring AI's `RewriteQueryTransformer` before retrieval. Show, with a stub
   model, that the rewritten query is what gets embedded.
4. **TOON context.** Format the retrieved chunks' metadata as TOON before augmentation. Measure the
   prompt token count before and after with `TokenCounter`.
5. **Threshold tuning.** With `-Pit` and `nomic-embed-text`, find the lowest threshold where the
   off-topic question still gets no sources.
6. **Real Docling.** Run `docling-serve` locally and write a `DoclingHttpParser` behind the same interface.
