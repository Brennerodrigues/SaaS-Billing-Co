package com.saas.billing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class JdbcCustomerRepository implements CustomerRepository {
    private final String url;
    private final String user;
    private final String password;

    public JdbcCustomerRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        String sql = "SELECT email, status FROM customers WHERE id = ?";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(new Customer(result.getString("email"), result.getString("status")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao consultar cliente.", e);
        }
    }

    @Override
    public void saveInvoice(String customerId, Money amount, boolean paid) {
        String sql = "INSERT INTO invoices(customer_id, amount, status, created_at) VALUES (?, ?, ?, NOW())";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customerId);
            statement.setBigDecimal(2, amount.amount());
            statement.setString(3, paid ? "PAID" : "FAILED");
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao salvar fatura.", e);
        }
    }
}
