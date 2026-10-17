# Exercises

1. **Checklist review.** Pick one gem marked *compare with* a blueprint. Answer the five questions in
   [theory.md](theory.md) for the gem and for the blueprint, then list one idea worth porting back.
2. **Port an idea, legally.** Choose a permissive-licence gem (`ReuseAdvice.PERMISSIVE_WITH_ATTRIBUTION`), port one
   small piece into the matching blueprint, and add the attribution and licence notice. Then do the same exercise
   with a `READ_ONLY` gem by re-implementing the idea from your notes without copying code.
3. **Grow the catalogue.** Add an eleventh handbook-style entry to `gems.csv` and the README. Run
   `mvn -B -pl part-2-github-gems -am verify`: `readmeAndCatalogueListTheSameRepositories` should fail until both agree,
   and the number check should fail until you update it. Decide whether the "10 entries" rule still belongs in a test.
4. **Licence drift.** Change one licence in `gems.csv` to a wrong value and run `mvn -Pit -pl part-2-github-gems verify`.
   Read the drift message, then revert.
5. **Rate limits.** `GitHubCatalogueIT` calls the API without a token (60 requests per hour). Make it read an optional
   `GITHUB_TOKEN` from the environment and send it as a bearer header, without ever logging it.
6. **Embabel spike.** Using the Embabel entry, try Blueprint 01's planner on Embabel in a branch on Spring Boot 4.1.x and
   record what changed (see Blueprint 01's README on why it isn't used here).
