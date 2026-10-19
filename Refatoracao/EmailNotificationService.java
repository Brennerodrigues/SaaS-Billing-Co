package com.saas.billing;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class EmailNotificationService implements NotificationService {
    @Override
    public void sendReceipt(String email, Money amount) {
        try {
            SimpleEmail sender = new SimpleEmail();
            sender.setHostName("smtp.empresa.com");
            sender.setSmtpPort(587);
            sender.setFrom("billing@empresa.com");
            sender.setSubject("Sua Fatura Mensal");
            sender.setMsg("Olá! Sua fatura foi processada no valor de R$ " + amount);
            sender.addTo(email);
            // sender.send(); // Envio desativado, como no código legado.
        } catch (EmailException e) {
            throw new IllegalStateException("Erro ao preparar recibo.", e);
        }
    }
}
