package edu.demo.banking;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

@WebServlet("/transfer")
public class TransferServlet extends HttpServlet {
    private static final BigDecimal MAX_TRANSFER = new BigDecimal("100000.00");
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        if(!Web.requireLogin(req,resp))return;
        String flash=(String)req.getSession().getAttribute("flash");req.getSession().removeAttribute("flash");
        String body="<div class=\"row justify-content-center\"><div class=\"col-lg-7\"><a href=\""+req.getContextPath()+"/dashboard\">← Dashboard</a><div class=\"card p-4 mt-3\"><h1 class=\"h3\">Transfer money</h1><p class=\"text-secondary\">Transfer between accounts registered in this demo.</p>"+(flash==null?"":Web.alert("danger",flash))+
          "<form method=\"post\" action=\""+req.getContextPath()+"/transfer\"><input type=\"hidden\" name=\"csrf\" value=\""+Web.esc(Web.csrf(req.getSession()))+"\"><input type=\"hidden\" name=\"idempotencyKey\" value=\""+UUID.randomUUID()+"\"><div class=\"mb-3\"><label class=\"form-label\">Recipient account number</label><input class=\"form-control\" name=\"recipient\" required maxlength=\"20\" autocomplete=\"off\"></div><div class=\"mb-3\"><label class=\"form-label\">Amount (INR)</label><input class=\"form-control\" type=\"number\" name=\"amount\" min=\"0.01\" max=\"100000\" step=\"0.01\" required></div><button class=\"btn btn-primary\">Review and transfer</button></form></div></div></div>";
        Web.page(resp,"Transfer",body);
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        if(!Web.requireLogin(req,resp))return;
        if(!Web.validCsrf(req)){resp.sendError(403,"Invalid form token. Reload the page.");return;}
        String recipient=req.getParameter("recipient")==null?"":req.getParameter("recipient").trim();
        String key=req.getParameter("idempotencyKey")==null?"":req.getParameter("idempotencyKey").trim();
        BigDecimal amount;
        try { amount=new BigDecimal(req.getParameter("amount")); }
        catch(Exception e){fail(req,resp,"Enter a valid amount.");return;}
        if(recipient.isBlank()||recipient.length()>20||!key.matches("^[0-9a-fA-F-]{36}$")||amount.scale()>2||amount.compareTo(BigDecimal.ZERO)<=0||amount.compareTo(MAX_TRANSFER)>0){
            fail(req,resp,"Check the recipient and enter an amount from ₹0.01 to ₹100,000.00.");return;
        }
        long customerId=Web.customerId(req);
        Connection c=null;
        try {
            c=Db.getConnection();c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            long sourceId;String sourceNo;String sourceStatus;
            try(PreparedStatement p=c.prepareStatement("SELECT account_id,account_number,status FROM accounts WHERE customer_id=? ORDER BY account_id LIMIT 1")) {
                p.setLong(1,customerId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("No source account found.");sourceId=r.getLong("account_id");sourceNo=r.getString("account_number");sourceStatus=r.getString("status");}
            }
            long targetId;String targetStatus;
            try(PreparedStatement p=c.prepareStatement("SELECT account_id,status FROM accounts WHERE account_number=?")) {
                p.setString(1,recipient);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Recipient account was not found.");targetId=r.getLong("account_id");targetStatus=r.getString("status");}
            }
            if(sourceId==targetId)throw new IllegalArgumentException("You cannot transfer to the same account.");
            if(!"ACTIVE".equals(sourceStatus)||!"ACTIVE".equals(targetStatus))throw new IllegalArgumentException("One of the accounts is not active.");
            // Lock both rows in ascending account_id order to reduce deadlocks.
            long low=Math.min(sourceId,targetId),high=Math.max(sourceId,targetId);
            BigDecimal sourceBalance=null,targetBalance=null;
            try(PreparedStatement p=c.prepareStatement("SELECT account_id,balance FROM accounts WHERE account_id IN (?,?) ORDER BY account_id FOR UPDATE")){
                p.setLong(1,low);p.setLong(2,high);
                try(ResultSet r=p.executeQuery()){while(r.next()){if(r.getLong("account_id")==sourceId)sourceBalance=r.getBigDecimal("balance");else targetBalance=r.getBigDecimal("balance");}}
            }
            if(sourceBalance==null||targetBalance==null)throw new IllegalArgumentException("Unable to lock the accounts.");
            if(sourceBalance.compareTo(amount)<0)throw new IllegalArgumentException("Insufficient balance.");
            String reference=UUID.randomUUID().toString();
            long transferId;
            try(PreparedStatement p=c.prepareStatement("INSERT INTO transfers(from_account_id,to_account_id,amount,reference_no,idempotency_key,status) VALUES(?,?,?,?,?,'SUCCESS')",Statement.RETURN_GENERATED_KEYS)){
                p.setLong(1,sourceId);p.setLong(2,targetId);p.setBigDecimal(3,amount);p.setString(4,reference);p.setString(5,key);p.executeUpdate();
                try(ResultSet r=p.getGeneratedKeys()){r.next();transferId=r.getLong(1);}
            }
            BigDecimal newSource=sourceBalance.subtract(amount),newTarget=targetBalance.add(amount);
            try(PreparedStatement p=c.prepareStatement("UPDATE accounts SET balance=? WHERE account_id=?")){
                p.setBigDecimal(1,newSource);p.setLong(2,sourceId);p.executeUpdate();
                p.setBigDecimal(1,newTarget);p.setLong(2,targetId);p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO ledger_entries(transfer_id,account_id,entry_type,amount,balance_after) VALUES(?,?,?,?,?),(?,?,?,?,?)")){
                p.setLong(1,transferId);p.setLong(2,sourceId);p.setString(3,"DEBIT");p.setBigDecimal(4,amount);p.setBigDecimal(5,newSource);
                p.setLong(6,transferId);p.setLong(7,targetId);p.setString(8,"CREDIT");p.setBigDecimal(9,amount);p.setBigDecimal(10,newTarget);p.executeUpdate();
            }
            c.commit();
            req.getSession().setAttribute("flash","Transfer successful. Reference: "+reference+" | From "+sourceNo+" to "+recipient+" | ₹"+amount.toPlainString());
            resp.sendRedirect(req.getContextPath()+"/transactions");
        } catch(IllegalArgumentException e) {
            rollback(c);fail(req,resp,e.getMessage());
        } catch(SQLIntegrityConstraintViolationException e) {
            rollback(c);fail(req,resp,"This transfer request was already processed. Refresh the form before making a new transfer.");
        } catch(Exception e) {
            rollback(c);getServletContext().log("Transfer failed",e);fail(req,resp,"Transfer could not be completed. No partial transfer was committed. Check the database and try again.");
        } finally {try{if(c!=null)c.close();}catch(Exception ignored){}}
    }
    private void rollback(Connection c){try{if(c!=null)c.rollback();}catch(Exception ignored){}}
    private void fail(HttpServletRequest req,HttpServletResponse resp,String message)throws IOException{
        req.getSession().setAttribute("flash",message);resp.sendRedirect(req.getContextPath()+"/transfer");
    }
}
