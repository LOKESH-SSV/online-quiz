# Online Quiz Application (Java AWT + SQLite + JDBC)

A desktop-based academic project with separate Student and Teacher panels.

## Features

### Student
- Student registration and login
- View published quizzes
- Attempt multiple-choice questions
- Quiz countdown based on configured duration
- Automatic score calculation
- View previous results
- One submission per student per quiz

### Teacher
- Teacher registration and login
- Create quizzes (initially saved as DRAFT)
- Add multiple-choice questions
- Publish quizzes after adding at least one question
- View results submitted by students

### Technical details
- Java 17
- Java AWT user interface
- SQLite database
- JDBC through Xerial SQLite JDBC
- PBKDF2WithHmacSHA256 password hashing with a random salt
- Prepared statements for database operations

## Requirements
- JDK 17 or newer
- Maven 3.8+
- IntelliJ IDEA, Eclipse, or VS Code with Java/Maven support

## Run the application

From the project root directory:

```bash
mvn clean compile
mvn exec:java
```

The SQLite database is created automatically at:

`database/online_quiz.db`

No database setup or external SQLite installation is required.

## First-time usage

1. Launch the application.
2. Select Student or Teacher on the login screen.
3. Click Create account and register.
4. Log in with the registered email and password.
5. As a teacher, create a quiz, add questions, then publish it.
6. Log in as a student, refresh available quizzes, select a quiz and attempt it.
7. Check the result in My Results (student) or View Results (teacher).

## Notes
- This is a local desktop application, not a hosted web application.
- The quiz timer is checked between question dialogs. It does not forcibly close an already-open question dialog at the exact deadline.
- The teacher registration is open for academic demonstration. For a production system, teacher accounts should require administrator approval.
- Do not commit real student personal data or the generated database to a public repository.
