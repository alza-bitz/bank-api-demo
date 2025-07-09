---
applyTo: '**'
---

For each feature slice:
 
- First slice: create account, view account
- Second slice: deposit, view account events (audit log)
- Third slice: withdraw, transfer

Implement DDD layers in turn:

1. Implement domain layer and persistence layer
2. Implement application layer
3. Implement interface layer

So we are iterating at two levels, the outer level by feature slice and the inner level by DDD layers.
