---
applyTo: '**'
---

The solution design is loosely based on the DDD layered approach. The meaning of "loosely" will be explained in the following sections.

# All layers
- Don't create any namespaces with the same name.
- Don't create any code that leaves unused vars.
- Don't create any code with redundant let expressions.
- Use Integrant for dependency injection and system lifecycle management.
- Use log4j2 for the logging implementation, and org.clojure/tools.logging for the logging api.

# Domain layer
- There is only one aggregate in the domain: the account, which is also the aggregate root.
- The account aggregate also includes an account event entity in a 1:n relationship.
- Accounts have global identity based on a uuid that will be provided by the domain layer and an auto-incrementing account number that will be provided by the persistence layer on transaction commit.
- Account events have global identity based on a uuid that will be provided by the domain layer, and also local identity based on a unique-per-account auto-incrementing sequence number that will be assigned by the persistence layer on transaction commit.
- Account events are produced by the following actions: deposit, withdraw, transfer to, transfer from.
- Account creation and account view do not produce account events.
- Use the domain events pattern to provide a function for each account action that takes an account plus other relevant args and returns a corresponding account event.
- These account action functions must throw an ExceptionInfo on any domain error with an :error key representing the domain error as a keyword.
- Use Malli to define specs for accounts, account actions and account events.

## Domain errors
- Withdraw money error keys: :insufficient-funds

# Persistence layer
- Use the DDD repository pattern to create an AccountRepository protocol.
- The repository will have a function for saving accounts that takes an account and returns a saved account.
- The repository will have a function for finding an account by account number that returns the account or throws an ExceptionInfo with :error key :account-not-found
- The repository will have a function for saving an account event that takes an account and the event and returns the saved account event.

# Application layer
- The Application layer will initially be implemented using a synchronous API.
- Use the DDD application service pattern to create an AccountService protocol.
- Create a SyncAccountService record implementing the AccountService protocol.
- For account create, use the domain layer to create the account and then call the persistence layer repository.
- For account view, just call the persistence layer repository.
- For account actions, use the domain layer to create the account event and then call the persistence layer repository, passing the account and event.
- 
- Unit tests will mock the persistence layer functions.

# Interface layer
- The HTTP API contract should match the endpoints in [problem_statement_and_requirements.instructions.md](problem_statement_and_requirements.instructions.md)
- Use the Metosin libraries i.e. Reitit, Malli, Muuntaja, Jsonista for the web stack.
- Use Malli to define specs for the HTTP API endpoint requests and responses.
- The interface layer should catch any ExceptionInfo thrown by the other layers, extract the :error key, choose the HTTP response status code based on the value and include the :error in the reponse body.
- Expose the HTTP API contract and endpoints using OpenAPI 3.x
- 
- Unit tests will mock the application layer functions.

# System layer
- The system layer will include functions for starting and stopping the system including all dependencies.
- The system layer will include a main function to start the system.
- The system layer will include a shutdown hook to stop the system.
- The configured system should use the JdbcAccountRepository with a Hikari connection pool. The pool should be closed when the system is stopped.