package com.onlinequiz;

import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

public class Main {
    private static final Color NAVY = new Color(30, 48, 80);
    private static final Color BLUE = new Color(45, 105, 180);
    private static final Font TITLE = new Font("Arial", Font.BOLD, 23);
    private static final Font NORMAL = new Font("Arial", Font.PLAIN, 14);

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                Database.initialize();
                new LoginFrame().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                message(null, "Could not initialize database:\n" + e.getMessage(), "Database error");
            }
        });
    }

    private static void message(Frame owner, String text, String title) {
        JOptionPane.showMessageDialog(owner, text, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private static boolean validEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private static class LoginFrame extends Frame {
        private final TextField email = new TextField(25);
        private final TextField password = new TextField(25);
        private final Choice role = new Choice();

        LoginFrame() {
            super("Online Quiz Application - Login");
            setSize(470, 350);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(12, 12));
            setBackground(Color.WHITE);

            Panel heading = new Panel(new GridLayout(2, 1));
            heading.setBackground(NAVY);
            Label title = new Label("ONLINE QUIZ APPLICATION", Label.CENTER);
            title.setFont(TITLE);
            title.setForeground(Color.WHITE);
            Label subtitle = new Label("Sign in to continue", Label.CENTER);
            subtitle.setForeground(Color.WHITE);
            heading.add(title);
            heading.add(subtitle);
            add(heading, BorderLayout.NORTH);

            role.add("Student");
            role.add("Teacher");
            password.setEchoChar('*');

            Panel form = new Panel(new GridBagLayout());
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(8, 8, 8, 8);
            g.anchor = GridBagConstraints.WEST;
            addRow(form, g, 0, "Account type", role);
            addRow(form, g, 1, "Email", email);
            addRow(form, g, 2, "Password", password);
            add(form, BorderLayout.CENTER);

            Panel buttons = new Panel(new FlowLayout());
            Button login = button("Login");
            Button register = button("Create account");
            Button exit = button("Exit");
            buttons.add(login);
            buttons.add(register);
            buttons.add(exit);
            add(buttons, BorderLayout.SOUTH);

            login.addActionListener(e -> login());
            register.addActionListener(e -> {
                new RegisterFrame(role.getSelectedItem()).setVisible(true);
            });
            exit.addActionListener(e -> dispose());
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { dispose(); }
            });
        }

        private void login() {
            String selectedRole = role.getSelectedItem();
            String table = selectedRole.equals("Student") ? "students" : "teachers";
            String idColumn = selectedRole.equals("Student") ? "student_id" : "teacher_id";
            String mail = email.getText().trim();
            String pass = password.getText();

            if (!validEmail(mail) || pass.isEmpty()) {
                message(this, "Enter a valid email and password.", "Validation");
                return;
            }

            String sql = "SELECT " + idColumn + ", name, salt, password_hash FROM " + table + " WHERE email=?";
            try (Connection c = Database.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, mail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !PasswordUtil.verify(pass, rs.getString("salt"), rs.getString("password_hash"))) {
                        message(this, "Incorrect email or password.", "Login failed");
                        return;
                    }
                    int id = rs.getInt(1);
                    String name = rs.getString("name");
                    dispose();
                    if (selectedRole.equals("Student")) new StudentDashboard(id, name).setVisible(true);
                    else new TeacherDashboard(id, name).setVisible(true);
                }
            } catch (Exception ex) {
                message(this, "Login error: " + ex.getMessage(), "Error");
            }
        }
    }

    private static class RegisterFrame extends Frame {
        private final TextField name = new TextField(25);
        private final TextField email = new TextField(25);
        private final TextField password = new TextField(25);
        private final String role;

        RegisterFrame(String role) {
            super(role + " Registration");
            this.role = role;
            setSize(430, 330);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));
            password.setEchoChar('*');

            Label title = new Label("Create " + role + " Account", Label.CENTER);
            title.setFont(TITLE);
            title.setForeground(NAVY);
            add(title, BorderLayout.NORTH);

            Panel form = new Panel(new GridLayout(3, 2, 8, 12));
            form.add(new Label("Full name:"));
            form.add(name);
            form.add(new Label("Email:"));
            form.add(email);
            form.add(new Label("Password (min. 6 chars):"));
            form.add(password);
            add(form, BorderLayout.CENTER);

            Panel actions = new Panel();
            Button create = button("Register");
            Button close = button("Close");
            actions.add(create);
            actions.add(close);
            add(actions, BorderLayout.SOUTH);
            create.addActionListener(e -> register());
            close.addActionListener(e -> dispose());
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { dispose(); }
            });
        }

        private void register() {
            String n = name.getText().trim();
            String mail = email.getText().trim();
            String pass = password.getText();
            if (n.isEmpty() || !validEmail(mail) || pass.length() < 6) {
                message(this, "Enter a name, valid email and password with at least 6 characters.", "Validation");
                return;
            }
            String table = role.equals("Student") ? "students" : "teachers";
            String salt = PasswordUtil.newSalt();
            String hash = PasswordUtil.hash(pass, salt);
            String sql = "INSERT INTO " + table + "(name,email,password_hash,salt) VALUES(?,?,?,?)";
            try (Connection c = Database.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, n);
                ps.setString(2, mail);
                ps.setString(3, hash);
                ps.setString(4, salt);
                ps.executeUpdate();
                message(this, role + " account created successfully. You can now log in.", "Registration successful");
                dispose();
            } catch (SQLException ex) {
                if (ex.getMessage().toLowerCase().contains("unique"))
                    message(this, "This email is already registered for this account type.", "Registration failed");
                else message(this, "Registration error: " + ex.getMessage(), "Error");
            }
        }
    }

    private static class TeacherDashboard extends Frame {
        private final int teacherId;
        private final TextArea output = new TextArea("", 12, 65, TextArea.SCROLLBARS_VERTICAL_ONLY);

        TeacherDashboard(int id, String name) {
            super("Teacher Dashboard - " + name);
            teacherId = id;
            setSize(760, 520);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));
            Label heading = new Label("Welcome, " + name + " | Teacher Dashboard", Label.CENTER);
            heading.setFont(TITLE);
            heading.setForeground(NAVY);
            add(heading, BorderLayout.NORTH);
            output.setEditable(false);
            output.setFont(new Font("Monospaced", Font.PLAIN, 13));
            add(output, BorderLayout.CENTER);

            Panel controls = new Panel(new FlowLayout());
            Button create = button("Create Quiz");
            Button questions = button("Add Questions");
            Button publish = button("Publish Quiz");
            Button results = button("View Results");
            Button refresh = button("Refresh");
            Button logout = button("Logout");
            for (Button b : new Button[]{create, questions, publish, results, refresh, logout}) controls.add(b);
            add(controls, BorderLayout.SOUTH);

            create.addActionListener(e -> createQuiz());
            questions.addActionListener(e -> addQuestions());
            publish.addActionListener(e -> publishQuiz());
            results.addActionListener(e -> viewResults());
            refresh.addActionListener(e -> loadQuizzes());
            logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { dispose(); }
            });
            loadQuizzes();
        }

        private void loadQuizzes() {
            StringBuilder sb = new StringBuilder("YOUR QUIZZES\n==============================================\n");
            String sql = "SELECT quiz_id,title,subject,duration,status FROM quizzes WHERE teacher_id=? ORDER BY quiz_id DESC";
            try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, teacherId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) sb.append("#").append(rs.getInt("quiz_id")).append(" | ")
                            .append(rs.getString("title")).append(" | ").append(rs.getString("subject"))
                            .append(" | ").append(rs.getInt("duration")).append(" min | ")
                            .append(rs.getString("status")).append("\n");
                }
            } catch (SQLException e) { sb.append("Error: ").append(e.getMessage()); }
            output.setText(sb.toString());
        }

        private void createQuiz() {
            TextField title = new TextField(28);
            TextField subject = new TextField(28);
            TextField duration = new TextField("20", 8);
            Panel p = new Panel(new GridLayout(3, 2, 6, 8));
            p.add(new Label("Quiz title:")); p.add(title);
            p.add(new Label("Subject:")); p.add(subject);
            p.add(new Label("Duration (minutes):")); p.add(duration);
            if (JOptionPane.showConfirmDialog(this, p, "Create quiz (saved as DRAFT)",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            try {
                int mins = Integer.parseInt(duration.getText().trim());
                if (title.getText().trim().isEmpty() || subject.getText().trim().isEmpty() || mins <= 0)
                    throw new IllegalArgumentException();
                try (Connection c = Database.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO quizzes(teacher_id,title,subject,duration) VALUES(?,?,?,?)")) {
                    ps.setInt(1, teacherId);
                    ps.setString(2, title.getText().trim());
                    ps.setString(3, subject.getText().trim());
                    ps.setInt(4, mins);
                    ps.executeUpdate();
                }
                message(this, "Quiz created as DRAFT. Add questions, then publish it.", "Success");
                loadQuizzes();
            } catch (Exception ex) { message(this, "Enter valid quiz details and a positive duration.", "Invalid input"); }
        }

        private Integer ownedQuizId(String prompt) {
            String value = JOptionPane.showInputDialog(this, prompt);
            if (value == null) return null;
            try {
                int id = Integer.parseInt(value.trim());
                try (Connection c = Database.getConnection();
                     PreparedStatement ps = c.prepareStatement("SELECT quiz_id FROM quizzes WHERE quiz_id=? AND teacher_id=?")) {
                    ps.setInt(1, id); ps.setInt(2, teacherId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return id;
                    }
                }
                message(this, "Quiz ID not found under your account.", "Not found");
            } catch (Exception e) { message(this, "Enter a valid numeric quiz ID.", "Invalid input"); }
            return null;
        }

        private void addQuestions() {
            Integer id = ownedQuizId("Enter the ID of the DRAFT quiz:");
            if (id == null) return;
            try (Connection c = Database.getConnection();
                 PreparedStatement check = c.prepareStatement("SELECT status FROM quizzes WHERE quiz_id=?")) {
                check.setInt(1, id);
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next() || !rs.getString(1).equals("DRAFT")) {
                        message(this, "Questions can only be added to a DRAFT quiz.", "Not allowed");
                        return;
                    }
                }
            } catch (SQLException e) { message(this, e.getMessage(), "Error"); return; }

            TextField q = new TextField(35);
            TextField a = new TextField(25), b = new TextField(25), c = new TextField(25), d = new TextField(25);
            Choice correct = new Choice();
            for (String s : new String[]{"A", "B", "C", "D"}) correct.add(s);
            Panel p = new Panel(new GridLayout(6, 2, 5, 6));
            p.add(new Label("Question:")); p.add(q);
            p.add(new Label("Option A:")); p.add(a);
            p.add(new Label("Option B:")); p.add(b);
            p.add(new Label("Option C:")); p.add(c);
            p.add(new Label("Option D:")); p.add(d);
            p.add(new Label("Correct option:")); p.add(correct);
            if (JOptionPane.showConfirmDialog(this, p, "Add question", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) return;
            if (q.getText().trim().isEmpty() || a.getText().trim().isEmpty() || b.getText().trim().isEmpty()
                    || c.getText().trim().isEmpty() || d.getText().trim().isEmpty()) {
                message(this, "All question and option fields are required.", "Validation"); return;
            }
            String sql = "INSERT INTO questions(quiz_id,question_text,option_a,option_b,option_c,option_d,correct_option) VALUES(?,?,?,?,?,?,?)";
            try (Connection con = Database.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id); ps.setString(2, q.getText().trim());
                ps.setString(3, a.getText().trim()); ps.setString(4, b.getText().trim());
                ps.setString(5, c.getText().trim()); ps.setString(6, d.getText().trim());
                ps.setString(7, correct.getSelectedItem()); ps.executeUpdate();
                message(this, "Question added successfully.", "Success");
            } catch (SQLException ex) { message(this, ex.getMessage(), "Database error"); }
        }

        private void publishQuiz() {
            Integer id = ownedQuizId("Enter the DRAFT quiz ID to publish:");
            if (id == null) return;
            try (Connection c = Database.getConnection()) {
                int count;
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM questions WHERE quiz_id=?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); count = rs.getInt(1); }
                }
                if (count == 0) { message(this, "Add at least one question before publishing.", "Cannot publish"); return; }
                try (PreparedStatement ps = c.prepareStatement("UPDATE quizzes SET status='PUBLISHED' WHERE quiz_id=? AND teacher_id=? AND status='DRAFT'")) {
                    ps.setInt(1, id); ps.setInt(2, teacherId);
                    if (ps.executeUpdate() == 1) message(this, "Quiz published. Students can now attempt it.", "Success");
                    else message(this, "Quiz could not be published.", "Notice");
                }
                loadQuizzes();
            } catch (SQLException e) { message(this, e.getMessage(), "Error"); }
        }

        private void viewResults() {
            StringBuilder sb = new StringBuilder("STUDENT RESULTS\n============================================================\n");
            String sql = """
                SELECT q.title, s.name, s.email, r.score, r.total_questions, r.submitted_at
                FROM results r JOIN quizzes q ON q.quiz_id=r.quiz_id
                JOIN students s ON s.student_id=r.student_id
                WHERE q.teacher_id=? ORDER BY r.submitted_at DESC
                """;
            try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, teacherId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) sb.append(rs.getString("title")).append(" | ")
                            .append(rs.getString("name")).append(" (").append(rs.getString("email")).append(") | Score: ")
                            .append(rs.getInt("score")).append("/").append(rs.getInt("total_questions"))
                            .append(" | ").append(rs.getString("submitted_at")).append("\n");
                }
            } catch (SQLException e) { sb.append("Error: ").append(e.getMessage()); }
            TextArea area = new TextArea(sb.toString(), 18, 75, TextArea.SCROLLBARS_BOTH);
            area.setEditable(false);
            Dialog dialog = new Dialog(this, "Quiz Results", true);
            dialog.add(area); dialog.setSize(720, 400); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
        }
    }

    private record QuizInfo(int id, String title, String subject, int duration) {
        public String toString() { return id + " - " + title + " (" + subject + ", " + duration + " min)"; }
    }
    private record QuestionInfo(int id, String text, String[] options, String correct) {}

    private static class StudentDashboard extends Frame {
        private final int studentId;
        private final TextArea output = new TextArea("", 12, 65, TextArea.SCROLLBARS_VERTICAL_ONLY);
        private final List<QuizInfo> quizzes = new ArrayList<>();

        StudentDashboard(int id, String name) {
            super("Student Dashboard - " + name);
            studentId = id;
            setSize(720, 480);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));
            Label heading = new Label("Welcome, " + name + " | Student Dashboard", Label.CENTER);
            heading.setFont(TITLE); heading.setForeground(NAVY);
            add(heading, BorderLayout.NORTH);
            output.setEditable(false);
            add(output, BorderLayout.CENTER);

            Panel controls = new Panel();
            Button refresh = button("Available Quizzes");
            Button attempt = button("Attempt Selected Quiz");
            Button history = button("My Results");
            Button logout = button("Logout");
            controls.add(refresh); controls.add(attempt); controls.add(history); controls.add(logout);
            add(controls, BorderLayout.SOUTH);
            refresh.addActionListener(e -> loadQuizzes());
            attempt.addActionListener(e -> attemptQuiz());
            history.addActionListener(e -> showHistory());
            logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { dispose(); }
            });
            loadQuizzes();
        }

        private void loadQuizzes() {
            quizzes.clear();
            StringBuilder sb = new StringBuilder("AVAILABLE PUBLISHED QUIZZES\n==============================================\n");
            String sql = """
                SELECT q.quiz_id,q.title,q.subject,q.duration
                FROM quizzes q
                WHERE q.status='PUBLISHED'
                  AND NOT EXISTS (SELECT 1 FROM results r WHERE r.quiz_id=q.quiz_id AND r.student_id=?)
                ORDER BY q.quiz_id DESC
                """;
            try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        QuizInfo q = new QuizInfo(rs.getInt("quiz_id"), rs.getString("title"),
                                rs.getString("subject"), rs.getInt("duration"));
                        quizzes.add(q);
                        sb.append(q).append("\n");
                    }
                }
                if (quizzes.isEmpty()) sb.append("No new quizzes are currently available.\n");
            } catch (SQLException e) { sb.append("Error: ").append(e.getMessage()); }
            output.setText(sb.toString());
        }

        private void attemptQuiz() {
            if (quizzes.isEmpty()) { message(this, "No available quizzes. Click Available Quizzes to refresh.", "No quizzes"); return; }
            Choice choice = new Choice();
            for (QuizInfo q : quizzes) choice.add(q.toString());
            Panel p = new Panel(new FlowLayout());
            p.add(new Label("Choose quiz:")); p.add(choice);
            if (JOptionPane.showConfirmDialog(this, p, "Select quiz", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) return;
            QuizInfo selected = quizzes.get(choice.getSelectedIndex());
            List<QuestionInfo> questions = getQuestions(selected.id());
            if (questions.isEmpty()) { message(this, "This quiz has no questions.", "Quiz unavailable"); return; }

            List<String> answers = new ArrayList<>();
            long endTime = System.currentTimeMillis() + selected.duration() * 60_000L;
            for (int i = 0; i < questions.size(); i++) {
                QuestionInfo q = questions.get(i);
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0) {
                    message(this, "Time is up. Remaining questions will be submitted unanswered.", "Time up");
                    break;
                }
                String prompt = "Question " + (i + 1) + " of " + questions.size()
                        + "\nTime remaining: " + (remaining / 60000) + " min " + ((remaining / 1000) % 60) + " sec\n\n"
                        + q.text() + "\n\nA. " + q.options()[0] + "\nB. " + q.options()[1]
                        + "\nC. " + q.options()[2] + "\nD. " + q.options()[3];
                String[] choices = {"A", "B", "C", "D"};
                Object answer = JOptionPane.showInputDialog(this, prompt, "Online Quiz",
                        JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
                answers.add(answer == null ? "" : answer.toString());
                if (answer == null) {
                    int confirm = JOptionPane.showConfirmDialog(this, "Cancel this quiz attempt?",
                            "Confirm", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) return;
                    answers.set(answers.size() - 1, "");
                }
            }
            int score = 0;
            for (int i = 0; i < answers.size(); i++)
                if (!answers.get(i).isEmpty() && answers.get(i).equals(questions.get(i).correct())) score++;

            saveResult(selected.id(), questions, answers, score);
            message(this, "Quiz submitted successfully!\nYour score: " + score + " / " + questions.size(), "Quiz result");
            loadQuizzes();
        }

        private List<QuestionInfo> getQuestions(int quizId) {
            List<QuestionInfo> list = new ArrayList<>();
            String sql = "SELECT question_id,question_text,option_a,option_b,option_c,option_d,correct_option FROM questions WHERE quiz_id=? ORDER BY question_id";
            try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, quizId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(new QuestionInfo(rs.getInt("question_id"), rs.getString("question_text"),
                            new String[]{rs.getString("option_a"), rs.getString("option_b"),
                                    rs.getString("option_c"), rs.getString("option_d")}, rs.getString("correct_option")));
                }
            } catch (SQLException e) { message(this, e.getMessage(), "Database error"); }
            return list;
        }

        private void saveResult(int quizId, List<QuestionInfo> questions, List<String> answers, int score) {
            String insertResult = "INSERT INTO results(student_id,quiz_id,score,total_questions) VALUES(?,?,?,?)";
            try (Connection c = Database.getConnection()) {
                c.setAutoCommit(false);
                int resultId;
                try (PreparedStatement ps = c.prepareStatement(insertResult, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, studentId); ps.setInt(2, quizId);
                    ps.setInt(3, score); ps.setInt(4, questions.size());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not create result record.");
                        resultId = keys.getInt(1);
                    }
                }
                String answerSql = "INSERT INTO student_answers(result_id,question_id,selected_option) VALUES(?,?,?)";
                try (PreparedStatement ps = c.prepareStatement(answerSql)) {
                    for (int i = 0; i < answers.size() && i < questions.size(); i++) {
                        if (answers.get(i).isEmpty()) continue;
                        ps.setInt(1, resultId); ps.setInt(2, questions.get(i).id());
                        ps.setString(3, answers.get(i)); ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (Exception e) {
                message(this, "Could not save quiz result: " + e.getMessage(), "Submission error");
            }
        }

        private void showHistory() {
            StringBuilder sb = new StringBuilder("MY QUIZ HISTORY\n====================================================\n");
            String sql = """
                SELECT q.title,q.subject,r.score,r.total_questions,r.submitted_at
                FROM results r JOIN quizzes q ON q.quiz_id=r.quiz_id
                WHERE r.student_id=? ORDER BY r.submitted_at DESC
                """;
            try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) sb.append(rs.getString("title")).append(" | ")
                            .append(rs.getString("subject")).append(" | Score: ")
                            .append(rs.getInt("score")).append("/").append(rs.getInt("total_questions"))
                            .append(" | ").append(rs.getString("submitted_at")).append("\n");
                }
            } catch (SQLException e) { sb.append("Error: ").append(e.getMessage()); }
            TextArea area = new TextArea(sb.toString(), 16, 65, TextArea.SCROLLBARS_BOTH);
            area.setEditable(false);
            Dialog dialog = new Dialog(this, "My Results", true);
            dialog.add(area); dialog.setSize(650, 350); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
        }
    }

    private static void addRow(Panel panel, GridBagConstraints g, int row, String label, Component field) {
        g.gridx = 0; g.gridy = row; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        panel.add(new Label(label + ":"), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, g);
    }

    private static Button button(String text) {
        Button b = new Button(text);
        b.setFont(NORMAL);
        b.setBackground(BLUE);
        b.setForeground(Color.WHITE);
        return b;
    }
}
