# Exercises

1. Add a `RIGHT_SIZE` cause and a `resizeInstance` action. Give it a cost so it beats `scaleDownIdle` only for instances that are >60% utilised.
2. Make `auto-approve-limit-inr` depend on the service tier.
3. Port the action catalogue to Embabel `@Action` methods in a separate Boot 4.1.x project. Then compare the plans and the traces.
4. Add a cycle-budget test: the agent must escalate rather than run more than 10 cycles.
