package com.google.devrel.training.conference.servlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.utils.SystemProperty;


public class SendConfirmationEmail extends HttpServlet {

	private static final long serialVersionUID = -3928639686401844912L;

	private static final Logger LOGGER = Logger.getLogger(SendConfirmationEmail.class.getName());
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		LOGGER.log(Level.INFO, "Send confirmation email");
		
		sendEmail(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		LOGGER.log(Level.INFO, "Send confirmation email");
		
		sendEmail(req, resp);
	}
	
	
	
	private void sendEmail(HttpServletRequest req, HttpServletResponse resp) throws UnsupportedEncodingException {
		
		String email = req.getParameter("email");
		String info = req.getParameter("info");
		
		Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
		String body = "Hi, you have created a following conference.\n" + info;
		
		try {
            Message message = new MimeMessage(session);
            InternetAddress from = new InternetAddress(String.format("noreply@%s.appspotmail.com",
                            SystemProperty.applicationId.get()), "Conference Central");
            message.setFrom(from);
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(email, ""));
            message.setSubject("You created a new Conference!");
            message.setText(body);
            Transport.send(message);
        } catch (MessagingException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to send an mail to %s", email), e);
            throw new RuntimeException(e);
        }
	}
}
