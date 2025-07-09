---
applyTo: '**'
---

The solution design is based on the DDD approach.

# All layers
- Don't create any namespaces with the same name.
- Use Integrant for dependency injection and system lifecycle management.
- Use log4j2 for the logging implementation, and org.clojure/tools.logging for the logging api.

# Domain layer
- There is only one aggregate in the domain: the account, which is also the aggregate root.
- The account aggregate also includes an account event entity in a 1:n relationship.
- Accounts have global identity, based on an auto-incrementing number that will be provided by the persistence layer on transaction commit.
- Account events have global identity, based on an auto-incrementing number that will be provided by the persistence layer on transaction commit.
- Use Malli to define specs for accounts and account events.
- Define functions to create domain events. For this problem, these will be account events.

# Application layer
- Application layer will initially be implemented using a synchronous API.
- For account create or account view, just call the persistence layer repository.
- For account events, use domain layer to create the account event and then call the persistence layer repository, passing account and event.
- 
- Unit tests will mock the persistence layer functions.

# Interface layer
- The HTTP API contract should match the endpoints in [problem_statement_and_requirements.instructions.md](problem_statement_and_requirements.instructions.md)
- Use the Metosin libraries i.e. Reitit, Malli, Muuntaja, Jsonista for the web stack.
- Use Malli to define specs for the HTTP API endpoint requests and responses.
- 
- Unit tests will mock the application layer functions.

# System layer
- System layer will include a main function to start the system.
- System layer will include a shutdown hook to stop the system.