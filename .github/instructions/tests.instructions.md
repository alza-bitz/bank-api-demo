---
applyTo: '**'
---

# All tests
- Use Malli specs to generate test data
- Use use-fixtures from clojure.test for any test fixtures
- For interactive development, use the REPL for checking that test namespaces load and checking the tests pass, and NOT the command line.

# Unit tests
- If you need to mock any side effects, for example the persistence layer, use with-redefs or reify as needed, and use spy.core and spy.assert from the tortue/spy library to assert the correct numbers of calls with the expected args.
- For spot checks, you can run the unit tests on the command line with `clojure -M:test`

# Integration tests
- Integration tests have the metadata keyword ^:integration
- For spot checks, you can run the integration tests on the command line with `clojure -M:integration`
