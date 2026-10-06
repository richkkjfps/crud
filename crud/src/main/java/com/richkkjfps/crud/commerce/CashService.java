package com.richkkjfps.crud.commerce;

import com.richkkjfps.crud.config.AccessContext;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CashService {
 private final JdbcTemplate db;
 private final AccessContext access;
 public CashService(JdbcTemplate db, AccessContext access){this.db=db;this.access=access;}
 public record OpenRequest(BigDecimal amount, UUID requestId){}
 public record CloseRequest(BigDecimal countedAmount,String notes){}
 public record MovementRequest(String kind,BigDecimal amount,String reason,UUID requestId){}
 public static BigDecimal amount(BigDecimal value,boolean positive){
  if(value==null || value.signum()<0 || (positive && value.signum()==0) || value.scale()>2 || value.compareTo(new BigDecimal("999999999999.99"))>0)
   throw invalid("Informe um valor válido, com até duas casas decimais.");
  return value;
 }
 private static ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 public static String method(String method){
  if(!Set.of("CASH","PIX","DEBIT","CARD").contains(method==null?"":method)) throw invalid("Escolha dinheiro, Pix, débito ou crédito.");
  return method;
 }
 // All register operations lock the company first, then the register, before products or sales.
 public Integer openIfAny(){
  var actor=access.account();
  db.queryForObject("SELECT id FROM client_panel WHERE id=? FOR UPDATE",Integer.class,actor.getTenantId());
  var rows=db.queryForList("SELECT id FROM cash_session WHERE tenant_id=? AND account_id=? AND closed_at IS NULL FOR UPDATE",Integer.class,actor.getTenantId(),actor.getId());
  return rows.isEmpty() ? null : rows.getFirst();
 }
 public int requireOpen(){
  Integer id=openIfAny();
  if(id==null)throw invalid("Abra seu caixa antes de registrar esta operação.");
  return id;
 }
 public Map<String,Object> current(){
  var actor=access.account();
  var rows=db.queryForList("SELECT id FROM cash_session WHERE tenant_id=? AND account_id=? AND closed_at IS NULL",Integer.class,actor.getTenantId(),actor.getId());
  return rows.isEmpty()?Collections.singletonMap("session",null):Map.of("session",report(rows.getFirst()));
 }
 public List<Map<String,Object>> history(){
  var actor=access.account();
  return db.queryForList("SELECT id,operator_name,opened_at,closed_at FROM cash_session WHERE tenant_id=? AND (? OR account_id=?) ORDER BY id DESC LIMIT 100",actor.getTenantId(),!"USER".equals(actor.getRole()),actor.getId());
 }
 @Transactional public Map<String,Object> open(OpenRequest request){
  var actor=access.account();amount(request.amount(),false);
  if(request.requestId()==null)throw invalid("Identificador da abertura obrigatório.");
  db.queryForObject("SELECT id FROM client_panel WHERE id=? FOR UPDATE",Integer.class,actor.getTenantId());
  var previous=db.queryForList("SELECT id FROM cash_session WHERE tenant_id=? AND account_id=? AND request_id=?",Integer.class,actor.getTenantId(),actor.getId(),request.requestId());
  if(!previous.isEmpty())return report(previous.getFirst());
  if(!db.queryForList("SELECT id FROM cash_session WHERE tenant_id=? AND account_id=? AND closed_at IS NULL",actor.getTenantId(),actor.getId()).isEmpty())throw invalid("Você já tem um caixa aberto.");
  int id=db.queryForObject("INSERT INTO cash_session(tenant_id,account_id,operator_name,opening_amount,request_id) VALUES(?,?,?,?,?) RETURNING id",Integer.class,actor.getTenantId(),actor.getId(),actor.getName(),request.amount(),request.requestId());
  return report(id);
 }
 @Transactional public Map<String,Object> close(int id,CloseRequest request){
  amount(request.countedAmount(),false);
  if(request.notes()!=null && request.notes().length()>1000)throw invalid("Observação muito longa.");
  var actor=access.account();
  db.queryForObject("SELECT id FROM client_panel WHERE id=? FOR UPDATE",Integer.class,actor.getTenantId());
  var rows=db.queryForList("SELECT * FROM cash_session WHERE tenant_id=? AND account_id=? AND id=? FOR UPDATE",actor.getTenantId(),actor.getId(),id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Caixa não encontrado.");
  if(rows.getFirst().get("closed_at")==null)db.update("UPDATE cash_session SET closed_at=CURRENT_TIMESTAMP,counted_amount=?,notes=? WHERE tenant_id=? AND id=?",request.countedAmount(),request.notes(),actor.getTenantId(),id);
  return report(id);
 }
 @Transactional public Map<String,Object> move(MovementRequest request){
  amount(request.amount(),true);
  if(!Set.of("IN","OUT").contains(request.kind()==null?"":request.kind()) || request.reason()==null || request.reason().isBlank() || request.reason().length()>500 || request.requestId()==null)throw invalid("Informe o tipo, valor e motivo da movimentação.");
  int id=requireOpen();int tenant=access.tenantId();
  var previous=db.queryForList("SELECT session_id FROM cash_movement WHERE tenant_id=? AND request_id=?",Integer.class,tenant,request.requestId());
  if(!previous.isEmpty() && previous.getFirst()!=id)throw invalid("Movimentação já registrada em outro caixa.");
  if(previous.isEmpty())db.update("INSERT INTO cash_movement(tenant_id,session_id,kind,amount,reason,request_id) VALUES(?,?,?,?,?,?)",tenant,id,request.kind(),request.amount(),request.reason().trim(),request.requestId());
  return report(id);
 }
 @Transactional(readOnly=true) public Map<String,Object> report(int id){
  var actor=access.account();int tenant=actor.getTenantId();
  var rows=db.queryForList("SELECT * FROM cash_session WHERE tenant_id=? AND id=? AND (? OR account_id=?)",tenant,id,!"USER".equals(actor.getRole()),actor.getId());
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Caixa não encontrado.");
  Map<String,Object> result=new LinkedHashMap<>(rows.getFirst());
  var totals=db.queryForMap("SELECT COUNT(*) AS sales_count,COALESCE(SUM(total),0) AS revenue FROM store_sale WHERE tenant_id=? AND cash_session_id=?",tenant,id);
  result.putAll(totals);
  result.putAll(db.queryForMap("SELECT COALESCE(SUM(i.quantity*i.unit_cost),0) AS known_cost,COALESCE(SUM(i.quantity*(i.unit_price-i.unit_cost)) FILTER(WHERE i.unit_cost IS NOT NULL),0) AS gross_profit,COUNT(*) FILTER(WHERE i.unit_cost IS NULL) AS unknown_cost_items FROM store_sale_item i JOIN store_sale s ON s.tenant_id=i.tenant_id AND s.id=i.sale_id WHERE s.tenant_id=? AND s.cash_session_id=?",tenant,id));
  var payments=db.queryForList("SELECT payment_method,COALESCE(SUM(amount),0) AS amount FROM sale_payment WHERE tenant_id=? AND cash_session_id=? GROUP BY payment_method ORDER BY payment_method",tenant,id);
  var movements=db.queryForList("SELECT kind,amount,reason,created_at FROM cash_movement WHERE tenant_id=? AND session_id=? ORDER BY id",tenant,id);
  BigDecimal cash=BigDecimal.ZERO,received=BigDecimal.ZERO,in=BigDecimal.ZERO,out=BigDecimal.ZERO;
  for(var p:payments){BigDecimal value=(BigDecimal)p.get("amount");received=received.add(value);if("CASH".equals(p.get("payment_method")))cash=cash.add(value);}
  for(var m:movements){if("IN".equals(m.get("kind")))in=in.add((BigDecimal)m.get("amount"));else out=out.add((BigDecimal)m.get("amount"));}
  BigDecimal expected=((BigDecimal)result.get("opening_amount")).add(cash).add(in).subtract(out);
  result.put("payments",payments);result.put("movements",movements);result.put("received",received);result.put("cash_in",in);result.put("cash_out",out);result.put("expected_cash",expected);
  result.put("difference",result.get("counted_amount")==null?null:((BigDecimal)result.get("counted_amount")).subtract(expected));
  result.put("sales",db.queryForList("SELECT id,total,payment_type,created_at FROM store_sale WHERE tenant_id=? AND cash_session_id=? ORDER BY id",tenant,id));
  result.put("restocks",db.queryForList("SELECT r.quantity,r.cost_price,r.created_at,p.name FROM stock_replenishment r JOIN product p ON p.tenant_id=r.tenant_id AND p.id=r.product_id WHERE r.tenant_id=? AND r.cash_session_id=? ORDER BY r.id",tenant,id));
  return result;
 }
}
