---
applyTo: '**'
---

# Persistence layer
- Create a DatomicAccountRepository record implementing the AccountRepository protocol in a datomic sub-namespace.
- The DatomicAccountRepository will be transacting or querying immutable account facts.
- The find-account function should materialise the current state of an account based on latest account facts. Although for efficiency reasons, the latest balance should still be stored and available as a fact.
- The find-account-events function should materialise account events based on the history of fact assertions on the account balance resulting from credits, debits and transfers. 
- For the auto-incrementing account numbers on accounts inside the create account transaction, use a next-account-number database function.
- For the per-account auto-incrementing sequence numbers on account events inside the save account events transaction, use a next-account-event-sequence database function.
- 
- Unit tests should mock the Datomic API functions where possible.
- Integration tests will use the real Datomic API functions, backed by an in-memory Datomic instance.

# Application layer
- Integration tests will use the real datomic API functions, backed by an in-memory Datomic instance.

# Interface layer
- Integration tests will use the real datomic API functions, backed by an in-memory Datomic instance.