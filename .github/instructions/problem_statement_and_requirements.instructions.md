---
applyTo: '**'
---

# Banking API

## Summary

Create an HTTP API for managing banking accounts. Your API will have six features, each with its own endpoint. Additionally, there are some global requirements.

Deliver a link to a repository containing your project (e.g. GitHub). To get an impression of your way of working we want to be able to see your commit history.
## Global requirements
- The API and its tests should be written in Clojure.
- Automated tests prove each feature/requirement is correctly implemented.
- Make use of a database such that domain information (accounts etc.) is not lost on a system restart.
- The API is able to asynchronously process 1000 concurrent requests.
- Request and response bodies are in JSON format.

## Note on the use of AI / LLMs:
- we understand that you may use LLMs / AI to aid you in the production of a response
- your use of these tools should mimic how you would use these on a production project. i.e. if you use them, we expect you to:
    - make changes in small batches
    - be able to understand all code submitted
    - be able to justify the decisions you have made as a human in the loop
    - be able to discuss alernatives to your submitted code, and the trade-offs associated with those choices


## Feature 1 - Create a bank account

### Requirements

- You can create a bank account.
- Each bank account has a unique account number.
- Account numbers are generated automatically.
- New bank accounts start with a balance of 0.

### API

Endpoint:

    POST /account

Request body:


    {
        "name": "Mr. Black"
    }

Response body describes the created account:

    {
        "account-number": 1,
        "name": "Mr. Black",
        "balance": 0
    }

## Feature 2 - View a bank account

### Requirements

- You can retrieve an existing bank account.

### API

Endpoint

    GET /account/:id

Response body:

    {
        "account-number": 1,
        "name": "Mr. Black",
        "balance": 0
    }


## Feature 3 - Deposit money to an account

### Requirements

- You can deposit money to an existing bank account.
- You can only deposit a positive amount of money.

### API

Endpoint

    POST /account/:id/deposit

Request body:

    {
        "amount": 100
    }

The response body describes the new situation:

    {
        "account-number": 1,
        "name": "Mr. Black",
        "balance": 100
    }

## Feature 4 - Withdraw money from an account

### Requirements

- You can withdraw money from an existing bank account.
- You can only withdraw a positive amount of money.
- The resulting balance should not fall below zero.

### API

Endpoint

    POST /account/:id/withdraw

Request body:

    {
        "amount": 5
    }

The response body describes the new situation:

    {
        "account-number": 1,
        "name": "Mr. Black",
        "balance": 95
    }


## Feature 5 - Transfer money between accounts

### Requirements

- You can transfer money from one existing account to another existing account.
- You cannot transfer money from an account to itself.
- The resulting balance of the sending account should not fall below
  zero.

### API

Endpoint

    POST /account/:id/send

Request body:

    {
        "amount": 50,
        "account-number": 800
    }

Where `:id` describes the sender and `"account-number"` the receiver.

The response body describes the new situation of the sending
account:

    {
        "account-number": 1,
        "name": "Mr. Black",
        "balance": 45
    }

## Feature 6 - Retrieve account audit log

### Requirements

- You can retrieve the audit log of an account.
- The audit log consists of records describing the events on the account.
- The audit log records appear in reverse chronological
order.
- An audit record has the following fields:
  - `sequence`: incrementing transaction sequence number
  - `debit`: amount of money that was removed
  - `credit`: amount of money that was added
  - `description`: describes the action that took place. Possible values:
    - "withdraw"
    - "deposit"
    - "send to #900" (a transfer to account 900)
    - "receive from #800" (a transfer from account 800)

### API

Endpoint:

    GET /account/:id/audit

Assuming the following sequence of transactions:

- deposit $100 to account #1
- transfer $5 from account #1 to account #900
- transfer $10 from account #800 to account #1
- withdraw $20 from account #1

The endpoint responds with this body:

    [
        {
            "sequence": 3,
            "debit": 20,
            "description": "withdraw"
        },
        {
            "sequence": 2,
            "credit": 10,
            "description": "receive from #800"
        },
        {
            "sequence": 1,
            "debit": 5,
            "description": "send to #900"
        },
        {
            "sequence": 0,
            "credit": 100,
            "description": "deposit"
        }
    ]