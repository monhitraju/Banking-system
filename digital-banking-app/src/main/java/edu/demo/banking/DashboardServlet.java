package edu.demo.banking;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        if(!Web.requireLogin(req,resp))return;
        long customerId=Web.customerId(req);
        String email=Web.esc((String)req.getSession().getAttribute("email"));
        StringBuilder accounts=new StringBuilder();
        try(Connection c=Db.getConnection();PreparedStatement p=c.prepareStatement("SELECT account_number,balance,status FROM accounts WHERE customer_id=? ORDER BY account_id")) {
            p.setLong(1,customerId);
            try(ResultSet r=p.executeQuery()){while(r.next()){
                accounts.append("<div class=\"card p-4 mb-3\"><div class=\"text-secondary small\">ACCOUNT NUMBER</div><div class=\"h5\">")
                  .append(Web.esc(r.getString("account_number"))).append("</div><div class=\"text-secondary small mt-2\">AVAILABLE BALANCE</div><div class=\"money\">₹")
                  .append(r.getBigDecimal("balance").toPlainString()).append("</div><span class=\"badge text-bg-success align-self-start\">")
                  .append(Web.esc(r.getString("status"))).append("</span></div>");
            }}
        } catch(Exception e){getServletContext().log("Dashboard query failed",e);Web.page(resp,"Dashboard",Web.alert("danger","Could not load accounts. Check database setup."));return;}
        String csrf=Web.esc(Web.csrf(req.getSession()));
        String body="<div class=\"d-flex justify-content-between align-items-center mb-4\"><div><h1 class=\"h3 mb-1\">Dashboard</h1><div class=\"text-secondary\">Signed in as "+email+"</div></div><form method=\"post\" action=\""+req.getContextPath()+"/logout\"><input type=\"hidden\" name=\"csrf\" value=\""+csrf+"\"><button class=\"btn btn-outline-secondary\">Sign out</button></form></div>"
          + "<div class=\"row g-4\"><div class=\"col-lg-5\"><h2 class=\"h5\">Your accounts</h2>"+accounts+"</div><div class=\"col-lg-7\"><div class=\"card p-4\"><h2 class=\"h5\">Transfer money</h2><p class=\"text-secondary\">Send simulated money to another registered account.</p><form method=\"get\" action=\""+req.getContextPath()+"/transfer\"><button class=\"btn btn-primary\">Make a transfer</button></form><hr><a class=\"btn btn-outline-primary\" href=\""+req.getContextPath()+"/transactions\">View transaction history</a></div><div class=\"alert alert-warning mt-3\">Demo limit: ₹100,000 per transfer. Only accounts in this application can receive funds.</div></div></div>";
        Web.page(resp,"Dashboard",body);
    }
}
