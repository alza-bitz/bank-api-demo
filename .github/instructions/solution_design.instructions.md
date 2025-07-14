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

# Persistence layer
- Use the DDD repository pattern to create an AccountRepository protocol.
- The repository will have a function for saving accounts that takes an account and returns a saved account.
- The repository will have a function for finding an account by account number that returns the account or throws an ExceptionInfo with :error key :account-not-found
- The repository will have a function for saving an account event that takes an account and the event and returns the saved account event.

# Application layer
- Use the DDD application service pattern to create an AccountService protocol.
- The application layer will initially be implemented using a synchronous API.
- The application layer will later be enhanced with an asynchronous API in order to meet the "asynchronously process 1000 concurrent requests" requirement.

# Application layer (synchronous API)
- Create a SyncAccountService record implementing the AccountService protocol.
- For account create, use the domain layer to create the account and then call the persistence layer repository.
- For account view, just call the persistence layer repository.
- For account actions, use the domain layer to create the account event and then call the persistence layer repository, passing the account and event.
- 
- Unit tests will mock the persistence layer functions.

# Application layer (asynchronous API)
- Create an AsyncAccountService record implementing the AccountService protocol.
- Unlike the SyncAccountService, the async API will have two components, an account operation producer and a pool of account operation consumers, connected by channels: a single operation channel and per-operation result channels.
- The order of per-account or between-account operations should not be expected to be preserved (non-deterministic).
- The producer puts messages on the operation channel, and takes messages from per-operation result channels.
- Consumers take messages from the operation channel, and put messages on per-operation result channels.
- Operations should be functions with exactly the same implementation as the functions for the synchronous implementation; essentially this is the command pattern.
- On taking a message from the operation channel, consumers should execute the operation, create a per-operation result channel and put a message to the result channel with either the operation result or any exception thrown.
- The AsyncAccountService could use an atom to share the required state between consumers and producers, a map of operation id to result channel.
- The AsyncAccountService should implement an AsyncOperationProducer protocol, providing a two functions: 1. a submit-operation function that takes an operation, generates a uuid as the operation id, puts both in a message to the operation channel and returns the operation id. 2. a retrieve-operation-result function that takes an operation id, and then takes from the per-operation result channel, converts the message to an operation result, closes the channel, removes it from the state atom and returns the operation result.
- 
- Integration tests will include end-to-end tests that reconcile the submitted operation id with a retrieved operation id.
- Integration tests for this layer don't need to cover the concurrent requirement, that will be covered in the integration tests for the interface layer.

# Interface layer (synchronous API)
- The HTTP API contract should match the endpoints in [problem_statement_and_requirements.instructions.md](problem_statement_and_requirements.instructions.md)
- Use the Metosin libraries i.e. Reitit, Malli, Muuntaja, Jsonista for the web stack.
- Use Malli to define specs for the HTTP API endpoint requests and responses.
- The interface layer should catch any ExceptionInfo thrown by the other layers, extract the :error key, choose the HTTP response status code based on the value and include the :error in the reponse body.
- Expose the HTTP API contract and endpoints using OpenAPI 3.x
- 
- Unit tests will mock the application layer functions.

# Interface layer (asynchronous API)
- The HTTP API contract for the async API will need to deviate slightly from [problem_statement_and_requirements.instructions.md](problem_statement_and_requirements.instructions.md).
- If the query string includes async=true, then existing endpoints will all return an operation submit response with the operation id, instead of the existing sync respsonses.
- There will be an additional new endpoint, GET /operation/:id/ that calls the AsyncOperationProducer retrieve-operation-result function, converts the operation result to an operation result repsonse and serialises to JSON as per the other API routes.
- 
- Integration tests will include end-to-end tests that reconcile the operation id in the operation submit response with the operation id in an operation result response.
- Integration tests will include concurrent tests to verify the "asynchronously process 1000 concurrent requests" requirement. 
- The concurrent tests could be made up of the following request workloads: a) 1000 create account API requests b) 1000 other API requests over 1-1000 existing accounts. This is because the other API requests need the account number of an existing account. Also, the request workloads and response assertions must consider that the order of per-account or between-account operations should not be expected to be preserved (non-deterministic).
- The concurrent tests should make use of the Malli generator functions for API requests.
- The concurrent tests might need to consider the size of the queue and thread pool for the jetty server.

# System layer
- The system layer will include functions for starting and stopping the system including all dependencies.
- The system layer will include a main function to start the system.
- The system layer will include a shutdown hook to stop the system.
- The configured system should use the JdbcAccountRepository with a Hikari connection pool. The pool should be closed when the system is stopped.
- 
- Integration tests should assert the system can be started without errors, and a started system can be stopped without errors.