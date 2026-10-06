package com.richkkjfps.crud.commerce;

import com.richkkjfps.crud.config.AccessContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final JdbcTemplate database;
    private final AccessContext access;
    private final CashService cash;

    public InventoryController(JdbcTemplate database, AccessContext access, CashService cash) {
        this.database = database;
        this.access = access;
        this.cash = cash;
    }

    @GetMapping("/profits")
    public Map<String, Object> profits() {
        int tenantId = access.administrator().getTenantId();
        return database.queryForMap(
            """
            SELECT COALESCE(SUM(unit_price*quantity),0) AS "revenue",
                COALESCE(SUM(unit_cost*quantity),0) AS "knownCost",
                COALESCE(SUM((unit_price-unit_cost)*quantity) FILTER (WHERE unit_cost IS NOT NULL),0) AS "grossProfit",
                COUNT(*) FILTER (WHERE unit_cost IS NULL) AS "unknownCostItems"
            FROM store_sale_item WHERE tenant_id=?
            """,
            tenantId
        );
    }

    @PostMapping("/products/{id}/restock")
    @Transactional
    public Map<String, Object> restock(
        @PathVariable Integer id,
        @RequestBody RestockRequest request
    ) {
        var actor = access.account();
        int tenantId = actor.getTenantId();
        if (request.quantity() == null || request.quantity() <= 0 || request.requestId() == null) {
            throw invalid("Informe uma quantidade positiva para a reposição.");
        }
        Integer sessionId = cash.openIfAny();
        var rows = database.queryForList(
            "SELECT quantity,cost_price,price FROM product WHERE tenant_id=? AND id=? FOR UPDATE",
            tenantId,
            id
        );
        if (rows.isEmpty()) throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Produto não encontrado nesta empresa."
        );
        var previous = database.queryForList(
            "SELECT product_id,stock_before,stock_after,cost_price,sale_price FROM stock_replenishment WHERE tenant_id=? AND request_id=?",
            tenantId,
            request.requestId()
        );
        if (!previous.isEmpty()) {
            if (((Number) previous.getFirst().get("product_id")).intValue() != id) throw invalid(
                "Esta reposição já pertence a outro produto."
            );
            return previous.getFirst();
        }
        var product = rows.getFirst();
        int before = ((Number) product.get("quantity")).intValue();
        long after = (long) before + request.quantity();
        if (after > Integer.MAX_VALUE) throw invalid("Estoque acima do limite permitido.");
        BigDecimal cost =
            ("USER".equals(actor.getRole()) || request.costPrice() == null)
                ? (BigDecimal) product.get("cost_price")
                : validPrice(request.costPrice());
        BigDecimal price =
            ("USER".equals(actor.getRole()) || request.price() == null)
                ? (BigDecimal) product.get("price")
                : validPrice(request.price());
        database.update(
            "UPDATE product SET quantity=?,cost_price=?,price=? WHERE tenant_id=? AND id=?",
            (int) after,
            cost,
            price,
            tenantId,
            id
        );
        database.update(
            "INSERT INTO stock_replenishment(tenant_id,product_id,quantity,stock_before,stock_after,cost_price,sale_price,request_id,created_by,cash_session_id) VALUES (?,?,?,?,?,?,?,?,?,?)",
            tenantId,
            id,
            request.quantity(),
            before,
            (int) after,
            cost,
            price,
            request.requestId(),
            actor.getId(), sessionId
        );
        return database.queryForMap(
            "SELECT product_id,stock_before,stock_after,cost_price,sale_price FROM stock_replenishment WHERE tenant_id=? AND request_id=?",
            tenantId,
            request.requestId()
        );
    }

    private BigDecimal validPrice(BigDecimal value) {
        if (
            value.signum() < 0 ||
            value.scale() > 2 ||
            value.compareTo(new BigDecimal("999999999999.99")) > 0
        ) throw invalid("Informe um valor com até duas casas decimais.");
        return value;
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record RestockRequest(
        Integer quantity,
        BigDecimal costPrice,
        BigDecimal price,
        UUID requestId
    ) {}
}
