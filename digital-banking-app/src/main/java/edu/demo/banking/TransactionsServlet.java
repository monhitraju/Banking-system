package edu.demo.banking;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/transactions")
public class TransactionsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        if(!Web.requireLogin(req,resp))return;
        StringBuilder rows=new StringBuilder();
        String flash=(String)req.getSession().getAttribute("flash");req.getSession().removeAttribute("flash");
        String sql="SELECT t.reference_no,t.amount,t.status,t.created_at,src.account_number AS source_no,dst.account_number AS target_no,src.customer_id AS source_owner,dst.customer_id AS target_owner FROM transfers t JOIN accounts src ON src.account_id=t.from_account_id JOIN accounts dst ON dst.account_id=t.to_account_id WHERE src.customer_id=? OR dst.customer_id=? ORDER BY t.created_at DESC LIMIT 100";
        try(Connection c=Db.getConnection();PreparedStatement p=c.prepareStatement(sql)){
            long id=Web.customerId(req);p.setLong(1,id);p.setLong(2,id);
            try(ResultSet r=p.executeQuery()){while(r.next()){
                boolean sent=r.getLong("source_owner")==id;
                rows.append("<tr><td>").append(Web.esc(r.getString("created_at"))).append("</td><td>")
                    .append(sent?"Sent":"Received").append("</td><td>")
                    .append(Web.esc(sent?r.getString("target_no"):r.getString("source_no"))).append("</td><td>")
                    .append(sent?"−":"＋").append("₹").append(r.getBigDecimal("amount").toPlainString())
                    .append("</td><td><span class=\"badge text-bg-success\">").append(Web.esc(r.getString("status"))).append("</span></td><td><code>")
                    .append(Web.esc(r.getString("reference_no"))).append("</code></td></tr>");
            }}
        }catch(Exception e){getServletContext().log("Transaction history failed",e);Web.page(resp,"History",Web.alert("danger","Unable to load transaction history."));return;}
        String body="<div class=\"d-flex justify-content-between align-items-center mb-4\"><div><a href=\""+req.getContextPath()+"/dashboard\">← Dashboard</a><h1 class=\"h3 mt-2\">Transaction history</h1></div><a class=\"btn btn-primary\" href=\""+req.getContextPath()+"/transfer\">New transfer</a></div>"+(flash==null?"":Web.alert("success",flash))+
          "<div class=\"card p-3\"><div class=\"table-responsive\"><table class=\"table table-hover align-middle\"><thead><tr><th>Date</th><th>Type</th><th>Other account</th><th>Amount</th><th>Status</th><th>Reference</th></tr></thead><tbody>"+(rows.length()==0?"<tr><td colspan=\"6\" class=\"text-center text-secondary py-4\">No transactions yet.</td></tr>":rows.toString())+"</tbody></table></div></div>";
        Web.page(resp,"Transaction history",body);
    }
}
