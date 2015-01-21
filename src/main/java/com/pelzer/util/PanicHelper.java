package com.pelzer.util;


import com.pelzer.util.spring.SpringUtil;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

public class PanicHelper {
  private static Logging.Logger log = Logging.getLogger(PanicHelper.class);

  public static boolean sendPanic(final String caller, final String message, final String... emails) {
    log.debug("Sending panic: {} {} to:{}", caller, message, emails);
    final MailSender mailSender = SpringUtil.getInstance().getBeanFactory().getBean("mailSender", MailSender.class);
    final SimpleMailMessage msg = new SimpleMailMessage();
    msg.setFrom(PanicConstants.FROM_ADDRESS);
    msg.setTo(emails);
    msg.setSubject("PANIC(" + PropertyManager.getEnvironment() + "): " + caller);
    msg.setText(message);
    mailSender.send(msg);
    return true;
  }

  public static class PanicConstants extends OverridableFields {
    public static String FROM_ADDRESS      = "foo@bar.com";
    public static String SYSADMIN_EMAILS[] = new String[] { "override.this@foo.com" };
    static {
      new PanicConstants().init();
    }
  }

  public static void main(final String[] args) {
    log.debug("About to send message...");
    sendPanic("Caller", "This is the message", "jason@pelzer.com");
    log.debug("Sent message.");
  }
}
