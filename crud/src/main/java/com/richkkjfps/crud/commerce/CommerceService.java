package com.richkkjfps.crud.commerce;

import com.richkkjfps.crud.commerce.CommerceDtos.*;
import com.richkkjfps.crud.config.AccessContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommerceService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private static final String SALE_QUERY = """
    SELECT s.*, c.name AS customer_name,
           COALESCE(p.paid,0) AS paid, s.total-COALESCE(p.paid,0) AS balance
    FROM store_sale s
    LEFT JOIN store_customer c ON c.tenant_id=s.tenant_id AND c.id=s.customer_id
    LEFT JOIN (SELECT tenant_id,sale_id,SUM(amount) paid FROM sale_payment
               GROUP BY tenant_id,sale_id) p ON p.tenant_id=s.tenant_id AND p.sale_id=s.id
    WHERE s.tenant_id=?
    """;

    private final JdbcTemplate database;
    private final AccessContext access;
    private final CashService cash;

    public CommerceService(JdbcTemplate database, AccessContext access, CashService cash) {
        this.database = database;
        this.access = access;
        this.cash = cash;
    }

    public List<Contact> contacts(boolean suppliers) {
        int tenantId = access.tenantId();
        if (suppliers) {
            return database.query(
                "SELECT *,0 AS balance FROM supplier WHERE tenant_id=? ORDER BY name,id",
                (row, index) -> contact(row, true),
                tenantId
            );
        }
        return database.query(
            """
            SELECT c.*, COALESCE(d.balance,0) AS balance FROM store_customer c
            LEFT JOIN (SELECT s.tenant_id,s.customer_id,
                SUM(s.total-COALESCE(p.paid,0)) balance FROM store_sale s
                LEFT JOIN (SELECT tenant_id,sale_id,SUM(amount) paid FROM sale_payment
                    GROUP BY tenant_id,sale_id) p ON p.tenant_id=s.tenant_id AND p.sale_id=s.id
                GROUP BY s.tenant_id,s.customer_id) d ON d.tenant_id=c.tenant_id AND d.customer_id=c.id
            WHERE c.tenant_id=? ORDER BY c.name,c.id
            """,
            (row, index) -> contact(row, false),
            tenantId
        );
    }

    @Transactional
    public Contact saveContact(boolean suppliers, Integer id, ContactRequest request) {
        int tenantId = access.tenantId();
        String table = suppliers ? "supplier" : "store_customer";
        String name = text(request.name(), 150, true);
        String phone = text(request.phone(), 40, false);
        String email = text(request.email(), 255, false);
        if (email != null && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw invalid("Informe um e-mail válido.");
        }
        String address = text(request.address(), 500, false);
        String notes = text(request.notes(), 3000, false);
        String suppliedItems = text(request.suppliedItems(), 5000, false);
        if (id == null) {
            id = suppliers
                ? database.queryForObject(
                      "INSERT INTO supplier(tenant_id,name,phone,email,address,notes,supplied_items) VALUES (?,?,?,?,?,?,?) RETURNING id",
                      Integer.class,
                      tenantId,
                      name,
                      phone,
                      email,
                      address,
                      notes,
                      suppliedItems
                  )
                : database.queryForObject(
                      "INSERT INTO store_customer(tenant_id,name,phone,email,address,notes) VALUES (?,?,?,?,?,?) RETURNING id",
                      Integer.class,
                      tenantId,
                      name,
                      phone,
                      email,
                      address,
                      notes
                  );
        } else {
            int changed = suppliers
                ? database.update(
                      "UPDATE supplier SET name=?,phone=?,email=?,address=?,notes=?,supplied_items=? WHERE tenant_id=? AND id=?",
                      name,
                      phone,
                      email,
                      address,
                      notes,
                      suppliedItems,
                      tenantId,
                      id
                  )
                : database.update(
                      "UPDATE store_customer SET name=?,phone=?,email=?,address=?,notes=? WHERE tenant_id=? AND id=?",
                      name,
                      phone,
                      email,
                      address,
                      notes,
                      tenantId,
                      id
                  );
            if (changed == 0) throw missing();
        }
        return new Contact(
            id,
            name,
            phone,
            email,
            address,
            suppliers ? suppliedItems : null,
            notes,
            suppliers ? BigDecimal.ZERO : customerBalance(tenantId, id)
        );
    }

    public List<Sale> sales(Integer customerId) {
        int tenantId = access.tenantId();
        return customerId == null
            ? database.query(SALE_QUERY + " ORDER BY s.id DESC", this::sale, tenantId)
            : database.query(
                  SALE_QUERY + " AND s.customer_id=? ORDER BY s.id DESC",
                  this::sale,
                  tenantId,
                  customerId
              );
    }

    public List<Sale> customerSales(Integer customerId) {
        requireCustomer(access.tenantId(), customerId);
        return sales(customerId);
    }

    public SaleDetails details(Integer id) {
        return details(access.tenantId(), id);
    }

    @Transactional
    public SaleDetails createSale(SaleRequest request) {
        var actor = access.account();
        int tenantId = actor.getTenantId();
        if (request.requestId() == null) throw invalid("Identificador da venda obrigatório.");

        // Serializa os cadastros da empresa e torna o reenvio da mesma venda seguro.
        database.queryForObject(
            "SELECT id FROM client_panel WHERE id=? FOR UPDATE",
            Integer.class,
            tenantId
        );
        var previous = database.queryForList(
            "SELECT id FROM store_sale WHERE tenant_id=? AND request_id=?",
            Integer.class,
            tenantId,
            request.requestId()
        );
        if (!previous.isEmpty()) return details(tenantId, previous.getFirst());
        int sessionId = cash.requireOpen();
        String paymentMethod = CashService.method(request.paymentMethod());

        boolean credit = "CREDIT".equals(request.paymentType());
        if (!credit && !"CASH".equals(request.paymentType())) throw invalid(
            "Escolha à vista ou fiado."
        );
        if (credit && request.customerId() == null) throw invalid(
            "Escolha o cliente para a venda no fiado."
        );
        if (request.customerId() != null) requireCustomer(tenantId, request.customerId());
        if (request.dueDate() != null && request.dueDate().isBefore(LocalDate.now())) {
            throw invalid("O vencimento não pode ser anterior à venda.");
        }
        String notes = text(request.notes(), 3000, false);
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 100) {
            throw invalid("Adicione de 1 a 100 produtos à venda.");
        }
        Map<Integer, Integer> quantities = new TreeMap<>();
        for (ItemRequest item : request.items()) {
            if (
                item == null ||
                item.productId() == null ||
                item.quantity() == null ||
                item.quantity() <= 0
            ) {
                throw invalid("Informe produtos e quantidades válidas.");
            }
            long combined = (long) quantities.getOrDefault(item.productId(), 0) + item.quantity();
            if (combined > Integer.MAX_VALUE) throw invalid("Quantidade acima do permitido.");
            quantities.put(item.productId(), (int) combined);
        }
        List<SaleItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : quantities.entrySet()) {
            var rows = database.queryForList(
                "SELECT id,name,price,cost_price,quantity FROM product WHERE tenant_id=? AND id=? FOR UPDATE",
                tenantId,
                entry.getKey()
            );
            if (rows.isEmpty()) throw missing();
            var product = rows.getFirst();
            if (((Number) product.get("quantity")).intValue() < entry.getValue()) {
                throw invalid("Estoque insuficiente para " + product.get("name") + ".");
            }
            BigDecimal price = (BigDecimal) product.get("price");
            total = total.add(price.multiply(BigDecimal.valueOf(entry.getValue())));
            items.add(
                new SaleItem(
                    entry.getKey(),
                    (String) product.get("name"),
                    entry.getValue(),
                    price,
                    (BigDecimal) product.get("cost_price")
                )
            );
        }
        money(total, false);
        BigDecimal paid = credit
            ? money(
                  request.initialPayment() == null ? BigDecimal.ZERO : request.initialPayment(),
                  false
              )
            : total;
        if (paid.compareTo(total) > 0) throw invalid(
            "A entrada não pode exceder o total da venda."
        );
        Integer id = database.queryForObject(
            """
            INSERT INTO store_sale(tenant_id,customer_id,payment_type,total,sale_date,due_date,notes,request_id,created_by,cash_session_id)
            VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
            """,
            Integer.class,
            tenantId,
            request.customerId(),
            request.paymentType(),
            total,
            LocalDate.now(),
            credit ? request.dueDate() : null,
            notes,
            request.requestId(),
            actor.getId(),
            sessionId
        );
        for (SaleItem item : items) {
            database.update(
                "INSERT INTO store_sale_item(tenant_id,sale_id,product_id,product_name,quantity,unit_price,unit_cost) VALUES (?,?,?,?,?,?,?)",
                tenantId,
                id,
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice(),
                item.unitCost()
            );
            database.update(
                "UPDATE product SET quantity=quantity-? WHERE tenant_id=? AND id=?",
                item.quantity(),
                tenantId,
                item.productId()
            );
        }
        if (paid.signum() > 0) {
            insertPayment(
                tenantId,
                id,
                paid,
                credit ? "Entrada da venda" : "Pagamento à vista",
                request.requestId(),
                actor.getId(), sessionId, paymentMethod
            );
        }
        return details(tenantId, id);
    }

    @Transactional
    public SaleDetails pay(Integer saleId, PaymentRequest request) {
        var actor = access.account();
        int tenantId = actor.getTenantId();
        BigDecimal amount = money(request.amount(), true);
        if (request.requestId() == null) throw invalid("Identificador do pagamento obrigatório.");
        String notes = text(request.notes(), 1000, false);
        int sessionId = cash.requireOpen();
        String paymentMethod = CashService.method(request.paymentMethod());
        var locked = database.queryForList(
            "SELECT id FROM store_sale WHERE tenant_id=? AND id=? FOR UPDATE",
            Integer.class,
            tenantId,
            saleId
        );
        if (locked.isEmpty()) throw missing();
        var previous = database.queryForList(
            "SELECT sale_id FROM sale_payment WHERE tenant_id=? AND request_id=?",
            Integer.class,
            tenantId,
            request.requestId()
        );
        if (!previous.isEmpty()) {
            if (!previous.getFirst().equals(saleId)) throw invalid(
                "Este pagamento já foi registrado em outra venda."
            );
            return details(tenantId, saleId);
        }
        Sale sale = findSale(tenantId, saleId);
        if (amount.compareTo(sale.balance()) > 0) throw invalid(
            "O pagamento não pode exceder o saldo devedor."
        );
        insertPayment(tenantId, saleId, amount, notes, request.requestId(), actor.getId(), sessionId, paymentMethod);
        return details(tenantId, saleId);
    }

    private void insertPayment(
        int tenantId,
        int saleId,
        BigDecimal amount,
        String notes,
        UUID requestId,
        int accountId, int sessionId, String paymentMethod
    ) {
        database.update(
            "INSERT INTO sale_payment(tenant_id,sale_id,amount,payment_date,notes,request_id,created_by,cash_session_id,payment_method) VALUES (?,?,?,?,?,?,?,?,?)",
            tenantId,
            saleId,
            amount,
            LocalDate.now(),
            notes,
            requestId,
            accountId, sessionId, paymentMethod
        );
    }

    private SaleDetails details(int tenantId, int id) {
        Sale sale = findSale(tenantId, id);
        var items = database.query(
            "SELECT * FROM store_sale_item WHERE tenant_id=? AND sale_id=? ORDER BY id",
            (row, index) ->
                new SaleItem(
                    row.getInt("product_id"),
                    row.getString("product_name"),
                    row.getInt("quantity"),
                    row.getBigDecimal("unit_price"),
                    manager() ? row.getBigDecimal("unit_cost") : null
                ),
            tenantId,
            id
        );
        var payments = database.query(
            "SELECT * FROM sale_payment WHERE tenant_id=? AND sale_id=? ORDER BY id",
            (row, index) ->
                new Payment(
                    row.getInt("id"),
                    row.getBigDecimal("amount"),
                    row.getObject("payment_date", LocalDate.class),
                    row.getString("notes")
                ),
            tenantId,
            id
        );
        return new SaleDetails(sale, items, payments);
    }

    private Sale findSale(int tenantId, int id) {
        var rows = database.query(SALE_QUERY + " AND s.id=?", this::sale, tenantId, id);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    private boolean manager() {
        return Set.of("ADMIN", "OWNER").contains(access.account().getRole());
    }

    private BigDecimal customerBalance(int tenantId, int customerId) {
        return database
            .query(SALE_QUERY + " AND s.customer_id=?", this::sale, tenantId, customerId)
            .stream()
            .map(Sale::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireCustomer(int tenantId, int id) {
        if (
            database
                .queryForList(
                    "SELECT id FROM store_customer WHERE tenant_id=? AND id=?",
                    Integer.class,
                    tenantId,
                    id
                )
                .isEmpty()
        ) {
            throw missing();
        }
    }

    private Contact contact(ResultSet row, boolean supplier) throws SQLException {
        return new Contact(
            row.getInt("id"),
            row.getString("name"),
            row.getString("phone"),
            row.getString("email"),
            row.getString("address"),
            supplier ? row.getString("supplied_items") : null,
            row.getString("notes"),
            row.getBigDecimal("balance")
        );
    }

    private Sale sale(ResultSet row, int index) throws SQLException {
        BigDecimal balance = row.getBigDecimal("balance");
        LocalDate due = row.getObject("due_date", LocalDate.class);
        String status =
            balance.signum() == 0
                ? "PAID"
                : due != null && due.isBefore(LocalDate.now())
                  ? "OVERDUE"
                  : "OPEN";
        return new Sale(
            row.getInt("id"),
            (Integer) row.getObject("customer_id"),
            row.getString("customer_name"),
            row.getString("payment_type"),
            row.getObject("sale_date", LocalDate.class),
            due,
            row.getBigDecimal("total"),
            row.getBigDecimal("paid"),
            balance,
            status,
            row.getString("notes")
        );
    }

    private String text(String value, int limit, boolean required) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() > limit || (required && cleaned.isEmpty())) throw invalid(
            "Confira os campos e os limites do cadastro."
        );
        return cleaned.isEmpty() ? null : cleaned;
    }

    private BigDecimal money(BigDecimal value, boolean positive) {
        if (
            value == null ||
            value.scale() > 2 ||
            value.signum() < 0 ||
            (positive && value.signum() == 0) ||
            value.compareTo(MAX_AMOUNT) > 0
        ) {
            throw invalid("Informe um valor válido, com até duas casas decimais.");
        }
        return value;
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Registro não encontrado nesta empresa."
        );
    }
}
