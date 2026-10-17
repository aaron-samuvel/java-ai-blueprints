# Theory: modular, branching, conditional RAG, and TOON

The handbook names this blueprint "Modular, branching, conditional RAG (Docling, PGVector)" and lists
TOON in Stage 3 and Part IV. The explanations below are **author's design (not from handbook)**, except
for the measured numbers, which this module's test produced.

## Modular RAG
Spring AI splits RAG into swappable stages inside `RetrievalAugmentationAdvisor`:

| Stage | Spring AI type | This module |
|---|---|---|
| Pre-retrieval (rewrite, expand, route) | `QueryTransformer`, `QueryExpander` | `KeywordQueryRouter` (used by the retriever) |
| Retrieval | `DocumentRetriever` | `RoutingDocumentRetriever` → `VectorStore` |
| Post-retrieval (join, rank) | `DocumentJoiner` | default concatenation |
| Generation | `QueryAugmenter` | `ContextualQueryAugmenter(allowEmptyContext=false)` |

Each stage is a single interface, so you can replace one (say, an LLM router) without touching the others.

## Branching vs conditional
- **Branching:** different queries take different paths. Here: smalltalk goes without RAG,
  policy questions search the policy domain, technical questions search the technical domain,
  and general questions search everything.
- **Conditional:** the same path changes behaviour based on intermediate results. If no chunk scores
  above the similarity threshold, the augmenter swaps the user query for a "politely decline"
  instruction. Off-topic questions then get an honest "I don't know" instead of a hallucination.
  The test asserts the off-topic query text never reaches the model.

## Why filter by metadata rather than use separate stores?
One pgvector table with a `domain` metadata column keeps ETL, back-ups and transactions simple
(handbook pillar 5: unified data). Filter expressions (`domain == 'policy'`) are translated to SQL
by `PgVectorStore`, and evaluated in memory by `SimpleVectorStore`.

## Docling
Docling turns PDFs and Office files into structured Markdown, keeping headings and tables, which makes
section-aware chunking work well. It runs as a Python service, so this repo ships `DoclingStubParser`
behind the same `DocumentParser` interface (see the README limits).

<a id="toon-measured"></a>
## TOON: measured, not assumed

TOON (Token-Oriented Object Notation) writes arrays of same-shaped objects once as a header
(`items[N]{a,b,c}:`) followed by CSV-like rows, so the keys aren't repeated in every row.
The handbook claims **40–50% token savings**. We measured it with
`ToonVsJsonTokenMeasurementTest`:
- data generated from fixed seeds, so it's reproducible;
- real BPE tokenizers from JTokkit;
- Jackson 3 for JSON;
- `ToonEncoder` for TOON (our subset implementation).

**Measured on 17 Oct 2026** (re-run with `mvn -pl blueprint-03-modular-rag -am verify`, which writes
`target/toon-measurement.md`):

| Dataset | Tokenizer | JSON pretty | JSON compact | TOON | TOON vs pretty | TOON vs compact |
|---|---|---|---|---|---|---|
| 100 uniform product rows (6 fields) | cl100k_base | 5969 | 3364 | 2045 | 65.7% | 39.2% |
| 100 uniform product rows (6 fields) | o200k_base | 5903 | 3299 | 2047 | 65.3% | 38.0% |
| 20 RAG chunks with metadata (4 fields, long text) | cl100k_base | 1259 | 874 | 732 | 41.9% | 16.2% |
| 20 RAG chunks with metadata (4 fields, long text) | o200k_base | 1263 | 879 | 736 | 41.7% | 16.3% |
| 20 nested, non-uniform orders | cl100k_base | 2306 | 1137 | 1484 | 35.6% | −30.5% |
| 20 nested, non-uniform orders | o200k_base | 2309 | 1181 | 1487 | 35.6% | −25.9% |

**What the numbers say:**
- **Uniform tables:** TOON saves about 38–39% against *compact* JSON, and about 65% against pretty JSON.
  That's close to, but a little under, the handbook's 40–50% when the baseline is compact JSON.
- **Long-text chunks:** savings drop to about 16% against compact JSON, because the text dominates and isn't compressed.
- **Nested, irregular data:** our TOON output is **26–31% larger** than compact JSON, and only beats pretty JSON.
- **Conclusion:** the 40–50% figure only holds for uniform, tabular payloads, or when the baseline is
  pretty-printed JSON. Use TOON for tabular context (search results, metadata, rows). Keep compact
  JSON for deeply nested structures.
- **Caveats:**
  - `ToonEncoder` is a subset implementation (author's design). The reference TOON tooling may produce different counts.
  - The tokenizers are OpenAI's BPE encodings. Llama-family tokenizers will give different absolute numbers.
