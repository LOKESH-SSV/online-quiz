package com.onlinequiz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class Database {
    private static final String URL;

    static {
        try {
            Path folder = Path.of("database").toAbsolutePath();
            Files.createDirectories(folder);
            URL = "jdbc:sqlite:" + folder.resolve("online_quiz.db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Database() {}

    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    public static void initialize() throws SQLException {
        String[] sql = {
            """
            CREATE TABLE IF NOT EXISTS students(
              student_id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              email TEXT NOT NULL COLLATE NOCASE UNIQUE,
              password_hash TEXT NOT NULL,
              salt TEXT NOT NULL,
              created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS teachers(
              teacher_id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              email TEXT NOT NULL COLLATE NOCASE UNIQUE,
              password_hash TEXT NOT NULL,
              salt TEXT NOT NULL,
              created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS quizzes(
              quiz_id INTEGER PRIMARY KEY AUTOINCREMENT,
              teacher_id INTEGER NOT NULL,
              title TEXT NOT NULL,
              subject TEXT NOT NULL,
              duration INTEGER NOT NULL CHECK(duration > 0),
              start_at TEXT,
              end_at TEXT,
              status TEXT NOT NULL DEFAULT 'DRAFT'
                CHECK(status IN ('DRAFT','PUBLISHED','CLOSED')),
              created_at TEXT DEFAULT CURRENT_TIMESTAMP,
              FOREIGN KEY(teacher_id) REFERENCES teachers(teacher_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS questions(
              question_id INTEGER PRIMARY KEY AUTOINCREMENT,
              quiz_id INTEGER NOT NULL,
              question_text TEXT NOT NULL,
              option_a TEXT NOT NULL,
              option_b TEXT NOT NULL,
              option_c TEXT NOT NULL,
              option_d TEXT NOT NULL,
              correct_option TEXT NOT NULL CHECK(correct_option IN ('A','B','C','D')),
              FOREIGN KEY(quiz_id) REFERENCES quizzes(quiz_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS results(
              result_id INTEGER PRIMARY KEY AUTOINCREMENT,
              student_id INTEGER NOT NULL,
              quiz_id INTEGER NOT NULL,
              score INTEGER NOT NULL DEFAULT 0,
              total_questions INTEGER NOT NULL,
              submitted_at TEXT DEFAULT CURRENT_TIMESTAMP,
              UNIQUE(student_id, quiz_id),
              FOREIGN KEY(student_id) REFERENCES students(student_id) ON DELETE CASCADE,
              FOREIGN KEY(quiz_id) REFERENCES quizzes(quiz_id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS student_answers(
              answer_id INTEGER PRIMARY KEY AUTOINCREMENT,
              result_id INTEGER NOT NULL,
              question_id INTEGER NOT NULL,
              selected_option TEXT NOT NULL CHECK(selected_option IN ('A','B','C','D')),
              UNIQUE(result_id, question_id),
              FOREIGN KEY(result_id) REFERENCES results(result_id) ON DELETE CASCADE,
              FOREIGN KEY(question_id) REFERENCES questions(question_id) ON DELETE CASCADE
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_quizzes_teacher ON quizzes(teacher_id)",
            "CREATE INDEX IF NOT EXISTS idx_questions_quiz ON questions(quiz_id)",
            "CREATE INDEX IF NOT EXISTS idx_results_student ON results(student_id)"
        };
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            for (String q : sql) s.execute(q);
            // Safe upgrades for databases created by earlier application versions.
            addColumnIfMissing(c, "quizzes", "start_at", "TEXT");
            addColumnIfMissing(c, "quizzes", "end_at", "TEXT");
        }
    }

    private static void addColumnIfMissing(Connection c, String table, String column, String type)
            throws SQLException {
        boolean exists = false;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equalsIgnoreCase(rs.getString("name"))) exists = true;
        }
        if (!exists) try (Statement s = c.createStatement()) {
            s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }
}
