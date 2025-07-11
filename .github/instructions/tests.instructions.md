---
applyTo: '**'
---

# All tests
- Use Malli specs to generate test data.
- Use use-fixtures from clojure.test for any test fixtures.
- For interactive development, use the REPL rather than the command line for checking that test namespaces load and checking the tests pass.
- For spot checks, you can run the tests for a specific namespace using `clojure -M:<alias> -n <test-ns>`

# Unit tests
- Unit tests must be placed in the `test` directory, with `-test` appended to the namespace of the code under test.
- If you need to mock any side effecting functions, use with-redefs or reify as needed, with spy.core and spy.assert from tortue/spy to assert the correct numbers of calls and expected args.
- If you need to mock any macros, use macroexpand beforehand to inspect the returned expansion and see what functions need to be mocked.
- For spot checks, you can run the unit tests on the command line with `clojure -M:test`

# Integration tests
- Integration tests must be placed in the `integration` directory, with `-integration-test` appended to the namespace of the code under test.
- For spot checks, you can run the integration tests on the command line with `clojure -M:integration`
