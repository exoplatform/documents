package org.exoplatform.documents.notification.utils;

import org.exoplatform.commons.api.notification.model.ArgumentLiteral;

public class NotificationConstants {

  public static final ArgumentLiteral<String> FROM_USER     = new ArgumentLiteral<>(String.class, "FROM_USER");

  public static final ArgumentLiteral<String> DOCUMENT_URL  = new ArgumentLiteral<>(String.class, "DOCUMENT_URL");

  public static final ArgumentLiteral<String> DOCUMENT_NAME = new ArgumentLiteral<>(String.class, "DOCUMENT_NAME");

  public static final ArgumentLiteral<String> RECEIVERS     = new ArgumentLiteral<>(String.class, "RECEIVERS");
}
