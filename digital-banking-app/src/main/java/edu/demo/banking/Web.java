package edu.demo.banking;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

public final class Web {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Web() {}

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#x27;");
    }
    public static String csrf(HttpSession session) {
        String token = (String) session.getAttribute("csrf");
        if (token == null) {
            byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute("csrf", token);
        }
        return token;
    }
    public static boolean validCsrf(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return false;
        String expected = (String) session.getAttribute("csrf");
        String supplied = req.getParameter("csrf");
        return expected != null && supplied != null &&
            java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static Long customerId(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s == null ? null : (Long) s.getAttribute("customerId");
    }
    public static boolean requireLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (customerId(req) == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return false;
        }
        return true;
    }
    public static void page(HttpServletResponse resp, String title, String body) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().write("""
          <!doctype html><html lang="en"><head><meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>%s</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
          <style>body{background:#f3f6fb}.navbar{background:#102a43}.card{border:0;box-shadow:0 5px 22px #102a4310}.brand{font-weight:800;letter-spacing:.2px}.money{font-size:2rem;font-weight:800}</style>
          </head><body><nav class="navbar navbar-dark mb-4"><div class="container"><a class="navbar-brand brand" href="%s/dashboard">Northstar Digital Bank</a></div></nav>
          <main class="container pb-5">%s</main><footer class="container text-secondary small pb-4">Educational simulation only — no real money is moved.</footer></body></html>
          """.formatted(esc(title), reqContext(resp), body));
    }
    // Kept simple: deployed app context path is /digital-banking.
    private static String reqContext(HttpServletResponse ignored) { return "/digital-banking"; }
    public static String alert(String type, String message) {
        return "<div class=\"alert alert-" + type + "\" role=\"alert\">" + esc(message) + "</div>";
    }
}
