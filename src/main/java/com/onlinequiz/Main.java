package com.onlinequiz;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.Timer;

public class Main {
    private static final Color NAVY = new Color(30, 48, 80);
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
            setSize(520, 370);
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
            Button studentRegister = button("Student Registration");
            Button teacherRegister = button("Teacher Registration");
            Button exit = button("Exit");
            buttons.add(login);
            buttons.add(studentRegister);
            buttons.add(teacherRegister);
            buttons.add(exit);
            add(buttons, BorderLayout.SOUTH);

            login.addActionListener(e -> login());
            studentRegister.addActionListener(e -> new RegisterFrame("Student").setVisible(true));
            teacherRegister.addActionListener(e -> new RegisterFrame("Teacher").setVisible(true));
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
            setSize(900, 600);
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
            Button editQuestions = button("Edit Questions");
            Button publish = button("Publish Quiz");
            Button results = button("View Results");
            Button edit = button("Edit Quiz");
            Button delete = button("Delete Quiz");
            Button schedule = button("Schedule Quiz");
            Button export = button("Export Results");
            Button refresh = button("Refresh");
            Button logout = button("Logout");
            for (Button b : new Button[]{create, questions, editQuestions, publish, results, edit, delete, schedule, export, refresh, logout}) controls.add(b);
            add(controls, BorderLayout.SOUTH);

            create.addActionListener(e -> createQuiz());
            questions.addActionListener(e -> addQuestions());
            editQuestions.addActionListener(e -> editQuestions());
            publish.addActionListener(e -> publishQuiz());
            results.addActionListener(e -> viewResults());
            edit.addActionListener(e -> editQuiz());
            delete.addActionListener(e -> deleteQuiz());
            schedule.addActionListener(e -> scheduleQuiz());
            export.addActionListener(e -> exportResults());
            refresh.addActionListener(e -> loadQuizzes());
            logout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) { dispose(); }
            });
            loadQuizzes();
        }

        private void loadQuizzes() {
            StringBuilder sb = new StringBuilder("YOUR QUIZZES\n==============================================\n");
            String sql = "SELECT quiz_id,title,subject,duration,status,start_at,end_at FROM quizzes WHERE teacher_id=? ORDER BY quiz_id DESC";
            try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, teacherId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) sb.append("#").append(rs.getInt("quiz_id")).append(" | ")
                            .append(rs.getString("title")).append(" | ").append(rs.getString("subject"))
                            .append(" | ").append(rs.getInt("duration")).append(" min | ")
                            .append(rs.getString("status")).append(" | ")
                            .append(rs.getString("start_at") == null ? "No start date" : rs.getString("start_at"))
                            .append(" to ").append(rs.getString("end_at") == null ? "No end date" : rs.getString("end_at"))
                            .append("\n");
                }
            } catch (SQLException e) { sb.append("Error: ").append(e.getMessage()); }
            output.setText(sb.toString());
        }

        private void createQuiz() {
            TextField title = new TextField(28);
            TextField subject = new TextField(28);
            TextField duration = new TextField("20", 8);
            TextField startAt = new TextField(20);
            TextField endAt = new TextField(20);
            Panel p = new Panel(new GridLayout(5, 2, 6, 8));
            p.add(new Label("Quiz title:")); p.add(title);
            p.add(new Label("Subject:")); p.add(subject);
            p.add(new Label("Duration (minutes):")); p.add(duration);
            p.add(new Label("Start (yyyy-MM-dd HH:mm, optional):")); p.add(startAt);
            p.add(new Label("End (yyyy-MM-dd HH:mm, optional):")); p.add(endAt);
            if (JOptionPane.showConfirmDialog(this, p, "Create quiz (saved as DRAFT)",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            try {
                int mins = Integer.parseInt(duration.getText().trim());
                if (title.getText().trim().isEmpty() || subject.getText().trim().isEmpty() || mins <= 0)
                    throw new IllegalArgumentException();
                String start = parseSchedule(startAt.getText());
                String end = parseSchedule(endAt.getText());
                if (start != null && end != null && !LocalDateTime.parse(start.replace(" ", "T")).isBefore(
                        LocalDateTime.parse(end.replace(" ", "T")))) throw new IllegalArgumentException();
                try (Connection c = Database.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO quizzes(teacher_id,title,subject,duration,start_at,end_at) VALUES(?,?,?,?,?,?)")) {
                    ps.setInt(1, teacherId);
                    ps.setString(2, title.getText().trim());
                    ps.setString(3, subject.getText().trim());
                    ps.setInt(4, mins);
                    ps.setString(5, start);
                    ps.setString(6, end);
                    ps.executeUpdate();
                }
                message(this, "Quiz created as DRAFT. Add questions, then publish it.", "Success");
                loadQuizzes();
            } catch (Exception ex) { message(this, "Enter valid quiz details and a positive duration.", "Invalid input"); }
        }

        private String parseSchedule(String value) {
            if (value == null || value.trim().isEmpty()) return null;
            return LocalDateTime.parse(value.trim().replace(" ", "T"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        private void editQuiz() {
            Integer id = ownedQuizId("Enter the quiz ID to edit:");
            if (id == null) return;
            try (Connection con = Database.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT title,subject,duration,start_at,end_at FROM quizzes WHERE quiz_id=? AND teacher_id=?")) {
                ps.setInt(1,id); ps.setInt(2,teacherId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return;
                    TextField title = new TextField(rs.getString("title"),28);
                    TextField subject = new TextField(rs.getString("subject"),28);
                    TextField duration = new TextField(String.valueOf(rs.getInt("duration")),8);
                    TextField start = new TextField(rs.getString("start_at") == null ? "" : rs.getString("start_at").substring(0,16),20);
                    TextField end = new TextField(rs.getString("end_at") == null ? "" : rs.getString("end_at").substring(0,16),20);
                    Panel p = new Panel(new GridLayout(5,2,6,8));
                    p.add(new Label("Title:")); p.add(title);
                    p.add(new Label("Subject:")); p.add(subject);
                    p.add(new Label("Duration:")); p.add(duration);
                    p.add(new Label("Start (yyyy-MM-dd HH:mm):")); p.add(start);
                    p.add(new Label("End (yyyy-MM-dd HH:mm):")); p.add(end);
                    if (JOptionPane.showConfirmDialog(this,p,"Edit Quiz #"+id,JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
                    int mins = Integer.parseInt(duration.getText().trim());
                    String startValue=parseSchedule(start.getText()), endValue=parseSchedule(end.getText());
                    if(title.getText().trim().isEmpty() || subject.getText().trim().isEmpty() || mins<=0) throw new IllegalArgumentException();
                    if(startValue!=null && endValue!=null && !LocalDateTime.parse(startValue.replace(" ","T")).isBefore(LocalDateTime.parse(endValue.replace(" ","T")))) throw new IllegalArgumentException();
                    try(PreparedStatement update=con.prepareStatement("UPDATE quizzes SET title=?,subject=?,duration=?,start_at=?,end_at=? WHERE quiz_id=? AND teacher_id=?")){
                        update.setString(1,title.getText().trim()); update.setString(2,subject.getText().trim());
                        update.setInt(3,mins); update.setString(4,startValue); update.setString(5,endValue);
                        update.setInt(6,id); update.setInt(7,teacherId); update.executeUpdate();
                    }
                    message(this,"Quiz details updated.","Success"); loadQuizzes();
                }
            } catch(Exception ex){ message(this,"Could not update quiz. Check the date format and details.","Edit error"); }
        }

        private void deleteQuiz() {
            Integer id=ownedQuizId("Enter the quiz ID to delete (its questions and results will also be deleted):");
            if(id==null) return;
            if(JOptionPane.showConfirmDialog(this,"Permanently delete quiz #"+id+" and its related questions/results?","Confirm deletion",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return;
            try(Connection con=Database.getConnection(); PreparedStatement ps=con.prepareStatement("DELETE FROM quizzes WHERE quiz_id=? AND teacher_id=?")){
                ps.setInt(1,id); ps.setInt(2,teacherId);
                message(this,ps.executeUpdate()==1?"Quiz deleted.":"Quiz was not deleted.","Delete quiz"); loadQuizzes();
            }catch(SQLException ex){message(this,ex.getMessage(),"Database error");}
        }

        private void scheduleQuiz() {
            Integer id=ownedQuizId("Enter the quiz ID to schedule:");
            if(id==null) return;
            TextField start=new TextField(20), end=new TextField(20);
            Panel p=new Panel(new GridLayout(2,2,6,8));
            p.add(new Label("Start (yyyy-MM-dd HH:mm):")); p.add(start);
            p.add(new Label("End (yyyy-MM-dd HH:mm):")); p.add(end);
            if(JOptionPane.showConfirmDialog(this,p,"Schedule quiz",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try{
                String a=parseSchedule(start.getText()), b=parseSchedule(end.getText());
                if(a==null || b==null || !LocalDateTime.parse(a.replace(" ","T")).isBefore(LocalDateTime.parse(b.replace(" ","T")))) throw new IllegalArgumentException();
                try(Connection con=Database.getConnection();PreparedStatement ps=con.prepareStatement("UPDATE quizzes SET start_at=?,end_at=? WHERE quiz_id=? AND teacher_id=?")){
                    ps.setString(1,a);ps.setString(2,b);ps.setInt(3,id);ps.setInt(4,teacherId);ps.executeUpdate();
                }
                message(this,"Quiz schedule saved.","Success");loadQuizzes();
            }catch(Exception ex){message(this,"Enter valid dates in yyyy-MM-dd HH:mm format. End must be after start.","Schedule error");}
        }

        private void exportResults() {
            Integer id=ownedQuizId("Enter the quiz ID whose results you want to export:");
            if(id==null)return;
            FileDialog chooser=new FileDialog(this,"Save results CSV",FileDialog.SAVE);
            chooser.setFile("quiz_"+id+"_results.csv"); chooser.setVisible(true);
            if(chooser.getFile()==null)return;
            File file=new File(chooser.getDirectory(),chooser.getFile());
            String sql="SELECT s.name,s.email,q.title,r.score,r.total_questions,r.submitted_at FROM results r JOIN students s ON s.student_id=r.student_id JOIN quizzes q ON q.quiz_id=r.quiz_id WHERE q.quiz_id=? AND q.teacher_id=? ORDER BY r.score DESC,r.submitted_at";
            try(Connection con=Database.getConnection();PreparedStatement ps=con.prepareStatement(sql); PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(file),java.nio.charset.StandardCharsets.UTF_8))){
                ps.setInt(1,id);ps.setInt(2,teacherId);
                out.println("Student Name,Email,Quiz Title,Score,Total Questions,Submitted At");
                try(ResultSet rs=ps.executeQuery()){
                    while(rs.next()) out.println(csv(rs.getString("name"))+","+csv(rs.getString("email"))+","+csv(rs.getString("title"))+","+rs.getInt("score")+","+rs.getInt("total_questions")+","+csv(rs.getString("submitted_at")));
                }
                message(this,"Results exported to:\n"+file.getAbsolutePath(),"Export successful");
            }catch(Exception ex){message(this,"Export failed: "+ex.getMessage(),"Export error");}
        }

        private String csv(String value) {
            if(value==null)return "\"\"";
            return "\"" + value.replace("\"", "\"\"") + "\"";
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

        private void editQuestions() {
            Integer quizId = ownedQuizId("Enter the quiz ID whose questions you want to edit:");
            if (quizId == null) return;

            Choice questionChoice = new Choice();
            List<Integer> questionIds = new ArrayList<>();
            try (Connection con = Database.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT question_id, question_text FROM questions WHERE quiz_id=? ORDER BY question_id")) {
                ps.setInt(1, quizId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        questionIds.add(rs.getInt("question_id"));
                        String text = rs.getString("question_text").replaceAll("\\s+", " ").trim();
                        if (text.length() > 55) text = text.substring(0, 52) + "...";
                        questionChoice.add("#" + rs.getInt("question_id") + " - " + text);
                    }
                }
            } catch (SQLException ex) {
                message(this, "Could not load questions: " + ex.getMessage(), "Database error");
                return;
            }

            if (questionIds.isEmpty()) {
                message(this, "This quiz does not contain any questions yet.", "No questions");
                return;
            }

            Panel selector = new Panel(new GridLayout(2, 1, 5, 5));
            selector.add(new Label("Select the question you want to edit:"));
            selector.add(questionChoice);
            if (JOptionPane.showConfirmDialog(this, selector, "Choose question",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

            int questionId = questionIds.get(questionChoice.getSelectedIndex());
            try (Connection con = Database.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT question_text,option_a,option_b,option_c,option_d,correct_option FROM questions WHERE question_id=? AND quiz_id=?")) {
                ps.setInt(1, questionId);
                ps.setInt(2, quizId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        message(this, "Question not found.", "Not found");
                        return;
                    }

                    TextField question = new TextField(rs.getString("question_text"), 38);
                    TextField optionA = new TextField(rs.getString("option_a"), 30);
                    TextField optionB = new TextField(rs.getString("option_b"), 30);
                    TextField optionC = new TextField(rs.getString("option_c"), 30);
                    TextField optionD = new TextField(rs.getString("option_d"), 30);
                    Choice correct = new Choice();
                    for (String option : new String[]{"A", "B", "C", "D"}) correct.add(option);
                    correct.select(rs.getString("correct_option"));

                    Panel form = new Panel(new GridLayout(6, 2, 6, 7));
                    form.add(new Label("Question:")); form.add(question);
                    form.add(new Label("Option A:")); form.add(optionA);
                    form.add(new Label("Option B:")); form.add(optionB);
                    form.add(new Label("Option C:")); form.add(optionC);
                    form.add(new Label("Option D:")); form.add(optionD);
                    form.add(new Label("Correct option:")); form.add(correct);

                    if (JOptionPane.showConfirmDialog(this, form, "Edit Question #" + questionId,
                            JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

                    if (question.getText().trim().isEmpty() || optionA.getText().trim().isEmpty()
                            || optionB.getText().trim().isEmpty() || optionC.getText().trim().isEmpty()
                            || optionD.getText().trim().isEmpty()) {
                        message(this, "All question and option fields are required.", "Validation");
                        return;
                    }

                    String updateSql = "UPDATE questions SET question_text=?,option_a=?,option_b=?,option_c=?,option_d=?,correct_option=? WHERE question_id=? AND quiz_id=?";
                    try (PreparedStatement update = con.prepareStatement(updateSql)) {
                        update.setString(1, question.getText().trim());
                        update.setString(2, optionA.getText().trim());
                        update.setString(3, optionB.getText().trim());
                        update.setString(4, optionC.getText().trim());
                        update.setString(5, optionD.getText().trim());
                        update.setString(6, correct.getSelectedItem());
                        update.setInt(7, questionId);
                        update.setInt(8, quizId);
                        update.executeUpdate();
                    }
                    message(this, "Question updated successfully. Existing student scores are not recalculated.",
                            "Success");
                    loadQuizzes();
                }
            } catch (SQLException ex) {
                message(this, "Could not update question: " + ex.getMessage(), "Database error");
            }
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
            Dialog dialog = new Dialog(this, "Individual Quiz Results", false);
            dialog.setLayout(new BorderLayout(8, 8));

            Choice quizChoice = new Choice();
            List<Integer> quizIds = new ArrayList<>();
            try (Connection con = Database.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT quiz_id,title,subject FROM quizzes WHERE teacher_id=? ORDER BY quiz_id DESC")) {
                ps.setInt(1, teacherId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        quizIds.add(rs.getInt("quiz_id"));
                        quizChoice.add("#" + rs.getInt("quiz_id") + " - " + rs.getString("title")
                                + " (" + rs.getString("subject") + ")");
                    }
                }
            } catch (SQLException ex) {
                message(this, "Could not load quizzes: " + ex.getMessage(), "Database error");
                return;
            }

            if (quizIds.isEmpty()) {
                message(this, "You have not created any quizzes yet.", "No quizzes");
                return;
            }

            TextArea area = new TextArea("", 18, 75, TextArea.SCROLLBARS_BOTH);
            area.setEditable(false);
            area.setFont(new Font("Monospaced", Font.PLAIN, 13));

            Panel selector = new Panel(new FlowLayout(FlowLayout.LEFT));
            selector.add(new Label("Select quiz:"));
            selector.add(quizChoice);
            dialog.add(selector, BorderLayout.NORTH);
            dialog.add(area, BorderLayout.CENTER);

            Button close = button("Close");
            Panel actions = new Panel(new FlowLayout(FlowLayout.RIGHT));
            actions.add(close);
            dialog.add(actions, BorderLayout.SOUTH);

            Runnable loadSelectedResults = () -> {
                int quizId = quizIds.get(quizChoice.getSelectedIndex());
                StringBuilder sb = new StringBuilder();
                sb.append("RESULTS FOR ").append(quizChoice.getSelectedItem()).append("\n");
                sb.append("======================================================================\n");
                sb.append(String.format("%-22s %-28s %-10s %-20s%n",
                        "STUDENT", "EMAIL", "SCORE", "SUBMITTED AT"));
                sb.append("----------------------------------------------------------------------\n");

                String sql = """
                    SELECT s.name,s.email,r.score,r.total_questions,r.submitted_at
                    FROM results r
                    JOIN students s ON s.student_id=r.student_id
                    JOIN quizzes q ON q.quiz_id=r.quiz_id
                    WHERE q.quiz_id=? AND q.teacher_id=?
                    ORDER BY r.submitted_at DESC
                    """;
                try (Connection con = Database.getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, quizId);
                    ps.setInt(2, teacherId);
                    try (ResultSet rs = ps.executeQuery()) {
                        int count = 0;
                        while (rs.next()) {
                            count++;
                            sb.append(String.format("%-22s %-28s %d/%d     %s%n",
                                    rs.getString("name"), rs.getString("email"),
                                    rs.getInt("score"), rs.getInt("total_questions"),
                                    rs.getString("submitted_at")));
                        }
                        if (count == 0) sb.append("No students have attempted this quiz yet.\n");
                        else sb.append("\nTotal submissions: ").append(count).append("\n");
                    }
                } catch (SQLException ex) {
                    sb.append("Could not load results: ").append(ex.getMessage());
                }
                area.setText(sb.toString());
                area.setCaretPosition(0);
            };

            quizChoice.addItemListener(e -> loadSelectedResults.run());
            // Keep the results window modeless so it never traps the teacher
            // in a modal event loop. Both Close and the title-bar X dispose it.
            close.addActionListener(e -> dialog.dispose());
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    dialog.dispose();
                }
            });

            loadSelectedResults.run();
            dialog.setSize(850, 460);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
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
            Button leaderboard = button("Leaderboard");
            Button logout = button("Logout");
            controls.add(refresh); controls.add(attempt); controls.add(history); controls.add(leaderboard); controls.add(logout);
            add(controls, BorderLayout.SOUTH);
            refresh.addActionListener(e -> loadQuizzes());
            attempt.addActionListener(e -> attemptQuiz());
            history.addActionListener(e -> showHistory());
            leaderboard.addActionListener(e -> showLeaderboard());
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
                  AND (q.start_at IS NULL OR datetime('now','localtime') >= datetime(q.start_at))
                  AND (q.end_at IS NULL OR datetime('now','localtime') <= datetime(q.end_at))
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
            if (quizzes.isEmpty()) { message(this,"No available quizzes. Refresh the list.","No quizzes"); return; }
            Choice choice=new Choice();
            for(QuizInfo q:quizzes)choice.add(q.toString());
            Panel select=new Panel(new FlowLayout());select.add(new Label("Choose quiz:"));select.add(choice);
            if(JOptionPane.showConfirmDialog(this,select,"Select quiz",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            QuizInfo selected=quizzes.get(choice.getSelectedIndex());
            List<QuestionInfo> questions=getQuestions(selected.id());
            if(questions.isEmpty()){message(this,"This quiz has no questions.","Unavailable");return;}
            String[] answers=new String[questions.size()];
            boolean[] review=new boolean[questions.size()];
            final int[] current={0};
            Dialog exam=new Dialog(this,"Quiz: "+selected.title(),true);
            exam.setLayout(new BorderLayout(10,10));exam.setSize(650,430);exam.setLocationRelativeTo(this);
            Label timerLabel=new Label("",Label.CENTER);timerLabel.setFont(new Font("Arial",Font.BOLD,18));
            Label progress=new Label("",Label.CENTER);
            TextArea questionText=new TextArea("",6,55,TextArea.SCROLLBARS_VERTICAL_ONLY);questionText.setEditable(false);
            Choice answerChoice=new Choice();answerChoice.add("Select answer");for(String opt:new String[]{"A","B","C","D"})answerChoice.add(opt);
            Panel center=new Panel(new BorderLayout(5,5));center.add(progress,BorderLayout.NORTH);center.add(questionText,BorderLayout.CENTER);
            Panel answerPanel=new Panel(new FlowLayout());answerPanel.add(new Label("Your answer:"));answerPanel.add(answerChoice);
            center.add(answerPanel,BorderLayout.SOUTH);
            exam.add(timerLabel,BorderLayout.NORTH);exam.add(center,BorderLayout.CENTER);
            Panel nav=new Panel(new FlowLayout());
            Button previous=button("Previous"),next=button("Next"),mark=button("Mark for Review"),submit=button("Submit Quiz");
            nav.add(previous);nav.add(next);nav.add(mark);nav.add(submit);exam.add(nav,BorderLayout.SOUTH);
            Runnable showQuestion=()->{
                QuestionInfo q=questions.get(current[0]);
                progress.setText("Question "+(current[0]+1)+" of "+questions.size()+" | "+(answers[current[0]]==null?"Not answered":"Answered")+(review[current[0]]?" | Marked for review":""));
                questionText.setText(q.text()+"\n\nA. "+q.options()[0]+"\nB. "+q.options()[1]+"\nC. "+q.options()[2]+"\nD. "+q.options()[3]);
                answerChoice.select(answers[current[0]]==null?"Select answer":answers[current[0]]);
                previous.setEnabled(current[0]>0);next.setEnabled(current[0]<questions.size()-1);
                mark.setLabel(review[current[0]]?"Unmark Review":"Mark for Review");
            };
            answerChoice.addItemListener(e->{answers[current[0]]=answerChoice.getSelectedIndex()==0?null:answerChoice.getSelectedItem();});
            previous.addActionListener(e->{answers[current[0]]=answerChoice.getSelectedIndex()==0?null:answerChoice.getSelectedItem();if(current[0]>0)current[0]--;showQuestion.run();});
            next.addActionListener(e->{answers[current[0]]=answerChoice.getSelectedIndex()==0?null:answerChoice.getSelectedItem();if(current[0]<questions.size()-1)current[0]++;showQuestion.run();});
            mark.addActionListener(e->{answers[current[0]]=answerChoice.getSelectedIndex()==0?null:answerChoice.getSelectedItem();review[current[0]]=!review[current[0]];showQuestion.run();});
            final boolean[] submitted={false};
            Runnable finish=()->{
                if(submitted[0])return;
                submitted[0]=true;
                int score=0;List<String> answerList=new ArrayList<>();
                for(int i=0;i<questions.size();i++){String a=answers[i]==null?"":answers[i];answerList.add(a);if(a.equals(questions.get(i).correct()))score++;}
                saveResult(selected.id(),questions,answerList,score);
                exam.dispose();
                message(this,"Quiz submitted!\nScore: "+score+" / "+questions.size(),"Quiz result");
                loadQuizzes();
            };
            submit.addActionListener(e->{
                answers[current[0]]=answerChoice.getSelectedIndex()==0?null:answerChoice.getSelectedItem();
                int confirm=JOptionPane.showConfirmDialog(exam,"Submit your quiz now?","Confirm submission",JOptionPane.YES_NO_OPTION);
                if(confirm==JOptionPane.YES_OPTION)finish.run();
            });
            exam.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){
                int confirm=JOptionPane.showConfirmDialog(exam,"Close and cancel this quiz attempt?","Cancel quiz",JOptionPane.YES_NO_OPTION);
                if(confirm==JOptionPane.YES_OPTION)exam.dispose();
            }});
            long deadline=System.currentTimeMillis()+selected.duration()*60_000L;
            Timer countdown=new Timer(1000,e->{
                long remaining=deadline-System.currentTimeMillis();
                if(remaining<=0){timerLabel.setText("Time remaining: 00:00");countdownStop((Timer)e.getSource());finish.run();}
                else timerLabel.setText(String.format("Time remaining: %02d:%02d",remaining/60000,(remaining/1000)%60));
            });
            showQuestion.run();
            timerLabel.setText("Time remaining: "+selected.duration()+":00");
            countdown.start();exam.setVisible(true);countdown.stop();
        }

        private void countdownStop(Timer timer){timer.stop();}

        private void showLeaderboard() {
            StringBuilder sb=new StringBuilder("LEADERBOARD - TOTAL QUIZ PERFORMANCE\n========================================================\n");
            String sql="SELECT s.name, SUM(r.score) AS marks, SUM(r.total_questions) AS total, COUNT(r.result_id) AS attempts FROM results r JOIN students s ON s.student_id=r.student_id GROUP BY s.student_id ORDER BY (1.0*SUM(r.score)/NULLIF(SUM(r.total_questions),0)) DESC, SUM(r.score) DESC, s.name LIMIT 50";
            try(Connection con=Database.getConnection();PreparedStatement ps=con.prepareStatement(sql);ResultSet rs=ps.executeQuery()){
                int rank=1;while(rs.next())sb.append(rank++).append(". ").append(rs.getString("name"))
                        .append(" | ").append(rs.getInt("marks")).append("/").append(rs.getInt("total"))
                        .append(" | Attempts: ").append(rs.getInt("attempts")).append("\n");
                if(rank==1)sb.append("No quiz results yet.");
            }catch(SQLException ex){sb.append("Error: ").append(ex.getMessage());}
            TextArea area=new TextArea(sb.toString(),18,65,TextArea.SCROLLBARS_VERTICAL_ONLY);
            area.setEditable(false);

            Dialog d=new Dialog(this,"Student Leaderboard",true);
            d.setLayout(new BorderLayout(8,8));
            d.add(area,BorderLayout.CENTER);

            Panel controls=new Panel(new FlowLayout(FlowLayout.RIGHT));
            Button close=button("Close");
            close.addActionListener(e -> d.dispose());
            controls.add(close);
            d.add(controls,BorderLayout.SOUTH);

            d.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    d.dispose();
                }
            });

            d.setSize(600,400);
            d.setLocationRelativeTo(this);
            d.setVisible(true);
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
        return b;
    }
}
