/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exoplatform.documents.notification.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.MockedStatic;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.channel.template.AbstractTemplateBuilder;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.plugin.NotificationPluginUtils;
import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.mail.MailPalette;
import org.exoplatform.commons.notification.template.TemplateUtils;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.portal.branding.BrandingService;
import org.exoplatform.portal.branding.MailDocumentShell;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.notification.LinkProviderUtils;

import groovy.text.GStringTemplateEngine;
import org.exoplatform.documents.notification.plugin.AddDocumentCollaboratorPlugin;
import org.exoplatform.documents.notification.plugin.ImportDocumentsPlugin;

/**
 * The mail of the notification is written in its recipient's palette: the
 * builder binds it, and the template renders on it, logo included.
 */
public class MailTemplatePaletteTest {

  private static final String COLLABORATOR_TEMPLATE =
      "../documents-webapp/src/main/webapp/WEB-INF/notification/templates/mail/AddDocumentCollaboratorPlugin.gtmpl";

  private static final String TEMPLATE = "../documents-webapp/src/main/webapp/WEB-INF/notification/templates/mail/ImportDocumentsPlugin.gtmpl";

  @Test
  public void theRecipientsPaletteIsBoundToTheMailsContext() {
    BrandingService brandingService = mock(BrandingService.class);
    when(brandingService.getDisplayMode("mary")).thenReturn("dark");
    when(brandingService.getMailPalette(true)).thenReturn(new HashMap<>(Map.of("surface", "#1E2328")));
    IdentityManager identityManager = mock(IdentityManager.class);
    Identity receiver = mock(Identity.class);
    Profile profile = mock(Profile.class);
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "mary")).thenReturn(receiver);
    when(receiver.getProfile()).thenReturn(profile);
    when(receiver.getRemoteId()).thenReturn("mary");
    when(profile.getProperty(Profile.FIRST_NAME)).thenReturn("Mary");
    when(profile.getFullName()).thenReturn("John Doe");
    when(profile.getUrl()).thenReturn("/john");
    NotificationInfo notification = mock(NotificationInfo.class);
    when(notification.getKey()).thenReturn(new PluginKey(ImportDocumentsPlugin.ID));
    when(notification.getValueOwnerParameter(anyString())).thenReturn("mary");
    when(notification.getTo()).thenReturn("mary");
    when(notification.getFrom()).thenReturn("john");
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.getNotificationInfo()).thenReturn(notification);
    AtomicReference<TemplateContext> rendered = new AtomicReference<>();

    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class);
         MockedStatic<NotificationPluginUtils> pluginUtils = mockStatic(NotificationPluginUtils.class);
         MockedStatic<LinkProviderUtils> linkProviderUtils = mockStatic(LinkProviderUtils.class);
         MockedStatic<TemplateUtils> templateUtils = mockStatic(TemplateUtils.class)) {
      commonsUtils.when(() -> CommonsUtils.getService(BrandingService.class)).thenReturn(brandingService);
      commonsUtils.when(CommonsUtils::getCurrentDomain).thenReturn("https://acme.test");
      pluginUtils.when(() -> NotificationPluginUtils.getLanguage(anyString())).thenReturn("en");
      linkProviderUtils.when(() -> LinkProviderUtils.getRedirectUrl(anyString(), anyString())).thenReturn("/settings");
      linkProviderUtils.when(LinkProviderUtils::getBaseUrl).thenReturn("https://acme.test");
      linkProviderUtils.when(() -> LinkProviderUtils.getUserAvatarUrl(any())).thenReturn("/avatar");
      templateUtils.when(() -> TemplateUtils.processSubject(any(TemplateContext.class))).thenReturn("subject");
      templateUtils.when(() -> TemplateUtils.processGroovy(any(TemplateContext.class))).thenAnswer(call -> {
        rendered.set(call.getArgument(0));
        return "body";
      });

      builder(identityManager).buildMessage(ctx);
    }

    TemplateContext templateContext = rendered.get();
    assertEquals("dark", MailPalette.of(templateContext).get(MailPalette.MODE));
    assertEquals("#1E2328", MailPalette.of(templateContext).get("surface"));
  }

  @Test
  public void theImportTemplateIsWrittenInTheRecipientsPalette() throws Exception {
    String html = render(TEMPLATE, darkPalette(), values());
    assertTrue(html, html.contains("bgcolor=\"#1E2328\""));
    assertTrue(html, html.contains("color:#5FA9AC") || html.contains("color: #5FA9AC"));
    assertTrue(html, html.contains("class=\"mail-surface mail-text\""));
    for (String literal : new String[] { "#ffffff", "#333333", "#2f5e92", "#d8d8d8", "#f9f9f9", "#efefef" }) {
      assertFalse("no light literal left: " + literal, html.contains(literal));
    }
  }

  @Test
  public void theCollaboratorTemplateIsWrittenInTheRecipientsPaletteWithTheLogo() throws Exception {
    String html = render(COLLABORATOR_TEMPLATE, darkPalette(), values());
    assertTrue(html, html.contains("bgcolor=\"#1E2328\""));
    assertTrue(html, html.contains("class=\"mail-surface mail-text\""));
    for (String literal : new String[] { "#ffffff", "#333333", "#2f5e92", "#d8d8d8", "#f9f9f9", "#efefef" }) {
      assertFalse("no light literal left: " + literal, html.contains(literal));
    }
  }

  @Test
  public void aContextWithoutPaletteRendersInTheLightDefaults() throws Exception {
    String html = render(TEMPLATE, null, values());
    assertTrue(html, html.contains("bgcolor=\"#FFFFFF\""));
    assertFalse(html, html.contains("null"));
  }

  private static AbstractTemplateBuilder builder(IdentityManager identityManager) {
    return new MailTemplateProvider(new InitParams(), identityManager).getTemplateBuilder()
                                                                      .get(PluginKey.key(ImportDocumentsPlugin.ID));
  }

  private static Map<String, Object> values() {
    Map<String, Object> values = new HashMap<>();
    for (String key : new String[] { "DOCUMENT_URL", "DOCUMENT_NAME", "FOLDER_URL", "TOTAL_NUMBER", "DURATION", "FILES_CREATED",
        "FILES_DUPLICATED", "FILES_UPDATED", "FILES_IGNORED", "FILES_FAILED", "USER", "AVATAR", "PROFILE_URL", "FIRST_NAME", "FOOTER_LINK" }) {
      values.put(key, key.toLowerCase());
    }
    return values;
  }

  private static Map<String, String> darkPalette() {
    Map<String, String> palette = new HashMap<>(MailDocumentShell.LIGHT_DEFAULTS);
    palette.put(MailDocumentShell.BACKGROUND, "#14181B");
    palette.put(MailDocumentShell.SURFACE, "#1E2328");
    palette.put(MailDocumentShell.TEXT, "#E6E9EC");
    palette.put(MailDocumentShell.SECONDARY_TEXT, "#9AA3AB");
    palette.put(MailDocumentShell.LINK, "#5FA9AC");
    palette.put(MailDocumentShell.BORDER, "#3A4148");
    palette.put(MailDocumentShell.COMPANY_NAME, "ACME");
    palette.put(MailPalette.MODE, "dark");
    return palette;
  }

  private static String render(String template, Map<String, String> palette, Map<String, Object> values) throws Exception {
    StringWriter writer = new StringWriter();
    TemplateContext ctx = new TemplateContext();
    ctx.put("_writer", writer);
    ctx.put("_templateContext", ctx);
    ctx.put("_ctx", new Element(ctx, writer));
    values.forEach(ctx::put);
    if (palette != null) {
      ctx.put(MailPalette.CONTEXT_KEY, palette);
    }
    try (MockedStatic<CommonsUtils> commonsUtils = mockStatic(CommonsUtils.class)) {
      commonsUtils.when(CommonsUtils::getCurrentDomain).thenReturn("https://acme.test");
      new GStringTemplateEngine().createTemplate(Files.readString(Paths.get(template), StandardCharsets.UTF_8))
                                 .make(ctx)
                                 .writeTo(writer);
    }
    return writer.toString();
  }

  /** The template's helper, as the mail channel gives it: resources, escaping and includes. */
  public static class Element {
    private final TemplateContext ctx;

    private final StringWriter    writer;

    public Element(TemplateContext ctx, StringWriter writer) {
      this.ctx = ctx;
      this.writer = writer;
    }

    public String appRes(String key, String... args) {
      return key;
    }

    public String escapeHTML(String value) {
      return value;
    }

    public void include(String path, TemplateContext context) {
      if (path.endsWith("NotificationLogo.gtmpl")) {
        writer.write(MailPalette.logoHtml(MailPalette.of(ctx)));
      } else {
        writer.write("<include " + path + "/>");
      }
    }
  }
}
