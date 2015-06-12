package com.pelzer.util;

import com.pelzer.util.spring.SpringUtil;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.ArrayList;
import java.util.List;

public class PanicHelper{
  private static Logging.Logger log = Logging.getLogger(PanicHelper.class);
  public static final List<PanicNotifier> panicNotifiers = new ArrayList<PanicNotifier>();

  static{
    // Add the default email notifier
    panicNotifiers.add(new PanicNotifier(){
      @Override
      public void handlePanic(final String caller, final String message){
        log.debug("Sending panic: {} {} to:{}", caller, message);
        final MailSender mailSender = SpringUtil.getInstance().getBean(MailSender.class);
        final SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(PanicConstants.FROM_ADDRESS);
        msg.setTo(PanicConstants.SYSADMIN_EMAILS);
        msg.setSubject("PANIC(" + PropertyManager.getEnvironment() + "): " + caller);
        msg.setText(message);
        mailSender.send(msg);
      }
    });
  }

  public static boolean sendPanic(final String caller, final String message){
    for(PanicNotifier notifier : panicNotifiers){
      try{
        notifier.handlePanic(caller, message);
      }catch(Exception ex){
        log.error("Exception", ex);
      }
    }
    return true;
  }

  public static class PanicConstants extends OverridableFields{
    public static String FROM_ADDRESS = "foo@bar.com";
    public static String SYSADMIN_EMAILS[] = new String[]{"override.this@foo.com"};

    static{
      new PanicConstants().init();
    }
  }

  public static interface PanicNotifier{
    void handlePanic(String caller, String message);
  }

  public static void main(final String[] args){
    log.debug("About to send message...");
    sendPanic("Caller", "This is the message");
    log.debug("Sent message.");
  }
}
