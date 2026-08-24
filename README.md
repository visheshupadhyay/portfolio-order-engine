Project title: Portfolio Order Engine
It will import orders from a file and save it to the inhousememory repository.
Features:- CREATED and PAID order states
- duplicate-payment protection
- file import
- malformed, invalid-status, and blank-ID row skipping
- repository lookup with Optional
- Maven and JUnit tests

Project structure: 
portfolio-order-engine/
│
├── pom.xml
├── src/
│   ├── main/
│   │   └── java/
│   │       └── App.java
│   │
│   └── test/
│       └── java/
│           └── AppTest.java
│
└── target/                   <- Maven Generated and git ignored
How to run:-
mvn test
mvn exec:java