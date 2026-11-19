# Online Quiz Application (Java AWT + SQLite + JDBC)

A desktop-based academic project with separate Student and Teacher panels.

## Features

### Student
- Student registration and login
- View published quizzes
- Attempt multiple-choice questions with a live countdown timer and automatic submission
- Navigate using Previous and Next, save answers, and mark questions for review
- Automatic score calculation and one submission per student per quiz
- View previous results
- View a leaderboard ranked by overall score percentage

### Teacher
- Teacher registration and login
- Create quizzes (initially saved as DRAFT)
- Add multiple-choice questions
- Publish quizzes after adding at least one question
- Edit quiz details and schedules, or delete quizzes
- Schedule quiz start and end date/time
- View results submitted by students
- Export a quiz's student results to CSV (opens in Excel)

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

## Scheduling format
Enter dates using `yyyy-MM-dd HH:mm` (24-hour time), for example `2026-12-01 09:30`. Leave both schedule fields blank if the quiz should be available as soon as it is published. When a schedule is set, students can start the quiz only between the configured start and end times.

## Notes
- This is a local desktop application, not a hosted web application.
- The countdown timer runs during the quiz and automatically submits answers when time expires.
- CSV exports contain student names, email addresses, quiz title, scores and submission timestamps. Store exported files securely.
- The teacher registration is open for academic demonstration. For a production system, teacher accounts should require administrator approval.
- Do not commit real student personal data or the generated database to a public repository.
