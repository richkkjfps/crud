package com.richkkjfps.crud;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.richkkjfps.crud.commerce.CashService;
import com.richkkjfps.crud.config.AccessContext;
import com.richkkjfps.crud.model.Users;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class CashServiceTests {
 @Test void registerRequiresAnOpenSessionForThisEmployee(){
  var db=mock(JdbcTemplate.class);var access=mock(AccessContext.class);var user=new Users();user.setId(7);user.setTenantId(2);when(access.account()).thenReturn(user);
  when(db.queryForList(anyString(),eq(Integer.class),eq(2),eq(7))).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->new CashService(db,access).requireOpen());
  when(db.queryForList(anyString(),eq(Integer.class),eq(2),eq(7))).thenReturn(List.of(19));
  assertEquals(19,new CashService(db,access).requireOpen());
 }
 @Test void moneyAndPaymentValidation(){
  assertThrows(ResponseStatusException.class,()->CashService.amount(new BigDecimal("-1"),false));
  assertThrows(ResponseStatusException.class,()->CashService.amount(new BigDecimal("1.001"),false));
  assertThrows(ResponseStatusException.class,()->CashService.amount(BigDecimal.ZERO,true));
  assertEquals(BigDecimal.ZERO,CashService.amount(BigDecimal.ZERO,false));
  for(String method:List.of("CASH","PIX","DEBIT","CARD"))assertEquals(method,CashService.method(method));
  assertThrows(ResponseStatusException.class,()->CashService.method(null));
 }
}
