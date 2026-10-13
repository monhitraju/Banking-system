package edu.demo.banking;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet(urlPatterns={"/","/login","/register","/logout"})
public class AuthServlet extends HttpServlet {
    private String base(HttpServletRequest r) { return r.getContextPath(); }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getServletPath();
        if ("/logout".equals(path)) { resp.sendRedirect(base(req)+"/login"); return; }
        if (Web.customerId(req) != null && ("/".equals(path) || "/login".equals(path))) {
            resp.sendRedirect(base(req)+"/dashboard"); return;
        }
        boolean register = "/register".equals(path);
        String csrf = Web.csrf(req.getSession(true));
        String error = (String) req.getSession().getAttribute("flash");
        req.getSession().removeAttribute("flash");
        String body = (error == null ? "" : Web.alert("danger", error)) + """
          <div class="row justify-content-center"><div class="col-md-6 col-lg-5"><div class="card p-4 p-md-5">
          <h1 class="h3 mb-2">%s</h1><p class="text-secondary mb-4">Secure access to your simulated account.</p>
          <form method="post" action="%s/%s">
          <input type="hidden" name="csrf" value="%s">
          %s
          <div class="mb-3"><label class="form-label">Email</label><input class="form-control" type="email" name="email" required maxlength="190" autocomplete="email"></div>
          <div class="mb-3"><label class="form-label">Password</label><input class="form-control" type="password" name="password" required minlength="8" autocomplete="%s"></div>
          <button class="btn btn-primary w-100" type="submit">%s</button></form>
          <p class="mt-4 mb-0 text-center">%s <a href="%s/%s">%s</a></p></div></div></div>
          """.formatted(register?"Create your account":"Welcome back",base(req),register?"register":"login",Web.esc(csrf),
            register?"<div class=\"mb-3\"><label class=\"form-label\">Full name</label><input class=\"form-control\" name=\"name\" required maxlength=\"100\" autocomplete=\"name\"></div>":"",
            register?"new-password":"current-password",register?"Register":"Sign in",
            register?"Already registered?":"New to Northstar?",base(req),register?"login":"register",register?"Sign in":"Create an account");
        Web.page(resp, register?"Register":"Sign in", body);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        String path = req.getServletPath();
        if ("/logout".equals(path)) {
            if (!Web.validCsrf(req)) { resp.sendError(403); return; }
            HttpSession s=req.getSession(false); if(s!=null)s.invalidate();
            resp.sendRedirect(base(req)+"/login"); return;
        }
        if (!Web.validCsrf(req)) { resp.sendError(403, "Invalid form token. Reload and try again."); return; }
        String email = req.getParameter("email") == null ? "" : req.getParameter("email").trim().toLowerCase();
        String password = req.getParameter("password") == null ? "" : req.getParameter("password");
        try {
            if ("/register".equals(path)) {
                String name = req.getParameter("name") == null ? "" : req.getParameter("name").trim();
                if(name.length()<2 || name.length()>100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || password.length()<8 || password.length()>72) {
                    throw new IllegalArgumentException("Enter a valid name/email and a password between 8 and 72 characters.");
                }
                try(Connection c=Db.getConnection()) {
                    c.setAutoCommit(false);
                    try {
                        long customerId;
                        try(PreparedStatement p=c.prepareStatement("INSERT INTO customers(full_name,email,password_hash) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                            p.setString(1,name);p.setString(2,email);p.setString(3,BCrypt.hashpw(password,BCrypt.gensalt(12)));p.executeUpdate();
                            try(ResultSet k=p.getGeneratedKeys()){k.next();customerId=k.getLong(1);}
                        }
                        String accountNo="NS"+java.util.UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
                        try(PreparedStatement p=c.prepareStatement("INSERT INTO accounts(customer_id,account_number,balance) VALUES(?,?,0.00)")) {
                            p.setLong(1,customerId);p.setString(2,accountNo);p.executeUpdate();
                        }
                        c.commit();
                    } catch(Exception e){c.rollback();throw e;}
                }
                req.getSession().setAttribute("flash","Registration successful. Please sign in.");
                resp.sendRedirect(base(req)+"/login"); return;
            }
            if (email.isBlank() || password.isBlank()) throw new IllegalArgumentException("Enter your email and password.");
            long customerId=-1; String hash=null; String status=null;
            try(Connection c=Db.getConnection();PreparedStatement p=c.prepareStatement("SELECT customer_id,password_hash,status FROM customers WHERE email=?")) {
                p.setString(1,email);
                try(ResultSet r=p.executeQuery()){if(r.next()){customerId=r.getLong("customer_id");hash=r.getString("password_hash");status=r.getString("status");}}
            }
            if(hash==null || !"ACTIVE".equals(status) || !BCrypt.checkpw(password,hash)) throw new IllegalArgumentException("Invalid email or password.");
            HttpSession old=req.getSession(false); if(old!=null)old.invalidate();
            HttpSession session=req.getSession(true); session.setAttribute("customerId",customerId);
            session.setAttribute("email",email); Web.csrf(session);
            resp.sendRedirect(base(req)+"/dashboard");
        } catch(IllegalArgumentException e) {
            req.getSession(true).setAttribute("flash",e.getMessage());
            resp.sendRedirect(base(req)+("/register".equals(path)?"/register":"/login"));
        } catch(SQLIntegrityConstraintViolationException e) {
            req.getSession(true).setAttribute("flash","That email is already registered.");
            resp.sendRedirect(base(req)+"/register");
        } catch(Exception e) {
            getServletContext().log("Authentication/database operation failed",e);
            req.getSession(true).setAttribute("flash","Unable to complete the request. Check database setup and try again.");
            resp.sendRedirect(base(req)+("/register".equals(path)?"/register":"/login"));
        }
    }
}
