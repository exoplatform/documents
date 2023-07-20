package org.exoplatform.documents.listener;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.Safe;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.model.*;
import org.exoplatform.portal.config.serialize.PortletApplication;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.navigation.NavigationService;
import org.exoplatform.portal.mop.navigation.NavigationStore;
import org.exoplatform.portal.mop.navigation.NodeData;
import org.exoplatform.portal.mop.page.PageKey;
import org.exoplatform.portal.pom.spi.portlet.Portlet;
import org.exoplatform.portal.webui.application.PortletState;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.SpaceTemplate;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent.Type;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleListener;
import org.exoplatform.social.core.space.spi.SpaceTemplateService;

public class DocumentsSpaceApplicationListener implements SpaceLifeCycleListener {

  private static final String[] DOCUMENTS_SIZE_GADGET_ACCESS_PERMISSIONS = new String[] { "Everyone" };

  public static final String    DOCUMENTS_GADGET_INSTALLED_EVENT_NAME    = "documents.space.application.installed";

  public static final String    DOCUMENTS_GADGET_UNINSTALLED_EVENT_NAME  = "documents.space.application.uniinstalled";

  public static final String    SPACE_HOME_EXTENSIBLE_CONTAINER_ID       = "SpaceHomePortlets";

  public static final String    DOCUMENTS_GADGET_NAME                    = "documents";

  public static final String    DOCUMENTS_DOCUMENTS_PORTLET_ID           = "Documents";

  public static final String    DOCUMENTS_DOCUMENTS_SIZE_PORTLET_ID      = "DocumentsSizeGadget";

  public static final String    DOCUMENTS_DOCUMENTS_SIZE_COMPLETE_ID     = DOCUMENTS_GADGET_NAME + "/"
      + DOCUMENTS_DOCUMENTS_SIZE_PORTLET_ID;

  private static final Log      LOG                                      =
                                    ExoLogger.getLogger(DocumentsSpaceApplicationListener.class);

  private ListenerService       listenerService;

  private SpaceTemplateService  spaceTemplateService;

  private NavigationService     navigationService;

  private NavigationStore       navigationStore;

  private DataStorage           dataStorage;

  @Override
  public void applicationAdded(SpaceLifeCycleEvent event) {
    installDocumentsSizeGadget(event);
  }

  @Override
  public void applicationActivated(SpaceLifeCycleEvent event) {
    installDocumentsSizeGadget(event);
  }

  @Override
  public void applicationRemoved(SpaceLifeCycleEvent event) {
    uninstallDocumentsSizeGadget(event);
  }

  @Override
  public void applicationDeactivated(SpaceLifeCycleEvent event) {
    uninstallDocumentsSizeGadget(event);
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    SpaceTemplate spaceTemplate = getSpaceTemplateService().getSpaceTemplateByName(space.getTemplate());
    if (spaceTemplate != null && spaceTemplate.getSpaceApplicationList() != null) {
      boolean documentAppInstalled = spaceTemplate.getSpaceApplicationList()
                                                .stream()
                                                .anyMatch(app -> StringUtils.equals(app.getPortletName(),
                                                                                    DOCUMENTS_DOCUMENTS_PORTLET_ID));
      if (documentAppInstalled) {
        try {
          installDocumentsSizeGadget(space, event.getType());
        } catch (Exception e) {
          LOG.warn("Error installing AgendaTimeline widget in space {}", space.getDisplayName(), e);
        }
      }
    }
  }

  @Override
  public void spaceRemoved(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void joined(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void left(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceAccessEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void addInvitedUser(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void addPendingUser(SpaceLifeCycleEvent event) {
    // Not needed
  }

  @Override
  public void spaceBannerEdited(SpaceLifeCycleEvent event) {
    // Not needed
  }

  private void installDocumentsSizeGadget(SpaceLifeCycleEvent event) {
    String appId = event.getTarget();
    if (StringUtils.isNotBlank(appId) && StringUtils.equals(getPortletId(appId), DOCUMENTS_DOCUMENTS_PORTLET_ID)) {
      Space space = event.getSpace();
      try {
        installDocumentsSizeGadget(space, event.getType());
      } catch (Exception e) {
        LOG.warn("Error installing Documents Size Gadget in space {}", space.getDisplayName(), e);
      }
    }
  }

  private void installDocumentsSizeGadget(Space space, Type type) throws Exception {
    boolean applicationExists = documentsSizeGadgetAddedInHomePage(space);
    if (!applicationExists) {
      Page spaceHomePage = getSpaceHomePage(space);
      if (spaceHomePage == null) {
        return;
      }

      Container extensibleSpaceHomeContainer = getExtensibleSpaceHomeContainer(spaceHomePage.getChildren());
      if (extensibleSpaceHomeContainer != null) {
        extensibleSpaceHomeContainer.getChildren().add(getDocumentsSizeGadgetModel());
        getDataStorage().save(spaceHomePage);
        broadcastEvent(getListenerService(), DOCUMENTS_GADGET_INSTALLED_EVENT_NAME, space, type);
      }
    }
  }

  private void uninstallDocumentsSizeGadget(SpaceLifeCycleEvent event) {
    String appId = event.getTarget();
    if (StringUtils.isNotBlank(appId) && StringUtils.equals(getPortletId(appId), DOCUMENTS_DOCUMENTS_PORTLET_ID)) {
      try {
        uninstallDocumentsSizeGadget(event.getSpace(), event.getType());
      } catch (Exception e) {
        LOG.warn("Error uninstalling Documents Size Gadget from space {}", event.getSpace().getDisplayName(), e);
      }
    }
  }

  private boolean documentsSizeGadgetAddedInHomePage(Space space) throws Exception {
    Page page = getSpaceHomePage(space);
    if (page == null) {
      LOG.info("Can't find home page content of space '{}', the  Documents Size Gadget will not be installed in Space Home page",
               space.getDisplayName());
      return false;
    }

    ArrayList<ModelObject> childObjects = page.getChildren();
    return documentsSizeGadgetExists(childObjects);
  }

  private void uninstallDocumentsSizeGadget(Space space, Type type) throws Exception {
    Page spaceHomePage = getSpaceHomePage(space);
    if (spaceHomePage == null) {
      return;
    }

    Container extensibleSpaceHomeContainer = getExtensibleSpaceHomeContainer(spaceHomePage.getChildren());
    if (extensibleSpaceHomeContainer != null) {
      ArrayList<ModelObject> children = extensibleSpaceHomeContainer.getChildren();
      boolean removed = removeDocumentsSizeGadget(children);
      if (removed) {
        getDataStorage().save(spaceHomePage);
        broadcastEvent(getListenerService(), DOCUMENTS_GADGET_UNINSTALLED_EVENT_NAME, space, type);
      }
    }
  }

  private boolean documentsSizeGadgetExists(ArrayList<ModelObject> childObjects) throws Exception {
    for (ModelObject modelObject : childObjects) {
      if (modelObject instanceof Container) {
        ArrayList<ModelObject> subChildren = ((Container) modelObject).getChildren();
        return documentsSizeGadgetExists(subChildren);
      } else if (modelObject instanceof Application) {
        Application<?> application = (Application<?>) modelObject;
        ApplicationState<?> state = application.getState();
        String applicationId = getDataStorage().getId(state);
        if (StringUtils.equals(DOCUMENTS_DOCUMENTS_SIZE_COMPLETE_ID, applicationId)) {
          return true;
        }
      }
    }
    return false;
  }

  private Container getExtensibleSpaceHomeContainer(ArrayList<ModelObject> childObjects) throws Exception {
    if (childObjects == null || childObjects.isEmpty()) {
      return null;
    }
    for (ModelObject modelObject : childObjects) {
      if (modelObject instanceof Container) {
        Container container = (Container) modelObject;
        if (StringUtils.equals(SPACE_HOME_EXTENSIBLE_CONTAINER_ID, container.getId())) {
          return container;
        }
        Container extensibleContainer = getExtensibleSpaceHomeContainer(container.getChildren());
        if (extensibleContainer != null) {
          return extensibleContainer;
        }
      }
    }
    return null;
  }

  private boolean removeDocumentsSizeGadget(ArrayList<ModelObject> childObjects) throws Exception {
    if (childObjects == null || childObjects.isEmpty()) {
      return false;
    }
    boolean removed = false;
    Iterator<ModelObject> childObjectsIterator = childObjects.iterator();
    while (childObjectsIterator.hasNext()) {
      ModelObject modelObject = childObjectsIterator.next();
      if (modelObject instanceof Application) {
        Application<?> application = (Application<?>) modelObject;
        ApplicationState<?> state = application.getState();
        String applicationId = getDataStorage().getId(state);
        if (StringUtils.equals(DOCUMENTS_DOCUMENTS_SIZE_COMPLETE_ID, applicationId)) {
          childObjectsIterator.remove();
          removed = true;
        }
      } else if (modelObject instanceof Container) {
        ArrayList<ModelObject> subChildren = ((Container) modelObject).getChildren();
        removed = removeDocumentsSizeGadget(subChildren);
      }
    }
    return removed;
  }

  private Page getSpaceHomePage(Space space) {
    Page homePage = getSpaceHomePageBySpaceTemplate(space);
    if (homePage != null) {
      return homePage;
    }
    return getSpaceHomePageFromNavigation(space);
  }

  private Page getSpaceHomePageFromNavigation(Space space) {
    String spaceDisplayName = space.getDisplayName();
    NavigationContext navigation = getNavigationService().loadNavigation(SiteKey.group(space.getGroupId()));
    if (navigation == null || navigation.getData() == null || navigation.getData().getRootId() == null) {
      LOG.debug("Can't find home page of space '{}', the  Documents Size Gadget will not be installed in Space Home page",
                spaceDisplayName);
      return null;
    }
    String rootId = navigation.getData().getRootId();
    PageKey homePageKey = getHomePageRef(rootId, spaceDisplayName);
    if (homePageKey == null) {
      LOG.debug("Can't find home page reference of space '{}', the  Documents Size Gadget will not be installed in Space Home page",
                spaceDisplayName);
      return null;
    }
    return getDataStorage().getPage(homePageKey.format());
  }

  private Page getSpaceHomePageBySpaceTemplate(Space space) {
    SpaceTemplate spaceTemplate = getSpaceTemplateService().getSpaceTemplateByName(space.getTemplate());
    if (spaceTemplate != null && spaceTemplate.getSpaceHomeApplication() != null) {
      String homePageName = spaceTemplate.getSpaceHomeApplication().getPortletName();
      PageKey homePageKey = new PageKey(SiteKey.group(space.getGroupId()), homePageName);
      return getDataStorage().getPage(homePageKey.format());
    }
    return null;
  }

  private PageKey getHomePageRef(String rootId, String spaceDisplayName) {
    NodeData node = getNavigationStore().loadNode(Safe.parseLong(rootId));
    if (node == null) {
      LOG.debug("Can't find home page of space '{}', the  Documents Size Gadget will not be installed in Space Home page",
                spaceDisplayName);
      return null;
    }

    if (node.getState() != null && node.getState().getPageRef() != null) {
      return node.getState().getPageRef();
    }

    Iterator<String> nodes = node.iterator(false);
    while (nodes.hasNext()) {
      String childId = nodes.next();
      PageKey homePageKey = getHomePageRef(childId, spaceDisplayName);
      if (homePageKey != null) {
        return homePageKey;
      }
    }
    return null;
  }

  private String getPortletId(String appId) {
    final char SEPARATOR = '.';

    if (appId.indexOf(SEPARATOR) != -1) {
      int beginIndex = appId.lastIndexOf(SEPARATOR) + 1;
      int endIndex = appId.length();

      return appId.substring(beginIndex, endIndex);
    }

    return appId;
  }

  private NavigationStore getNavigationStore() {
    if (navigationStore == null) {
      navigationStore = ExoContainerContext.getService(NavigationStore.class);
    }
    return navigationStore;
  }

  private NavigationService getNavigationService() {
    if (navigationService == null) {
      navigationService = ExoContainerContext.getService(NavigationService.class);
    }
    return navigationService;
  }

  private DataStorage getDataStorage() {
    if (dataStorage == null) {
      dataStorage = ExoContainerContext.getService(DataStorage.class);
    }
    return dataStorage;
  }

  private ListenerService getListenerService() {
    if (listenerService == null) {
      listenerService = ExoContainerContext.getService(ListenerService.class);
    }
    return listenerService;
  }

  private SpaceTemplateService getSpaceTemplateService() {
    if (spaceTemplateService == null) {
      spaceTemplateService = ExoContainerContext.getService(SpaceTemplateService.class);
    }
    return spaceTemplateService;
  }

  private static Application<Portlet> getDocumentsSizeGadgetModel() {
    PortletApplication model = new PortletApplication();
    PortletState<Portlet> state = new PortletState<>(new TransientApplicationState<>(DOCUMENTS_DOCUMENTS_SIZE_COMPLETE_ID),
                                                     ApplicationType.PORTLET);
    model.setState(state.getApplicationState());
    model.setTitle(DOCUMENTS_DOCUMENTS_SIZE_PORTLET_ID);
    model.setShowInfoBar(false);
    model.setShowApplicationState(false);
    model.setShowApplicationMode(false);
    model.setAccessPermissions(DOCUMENTS_SIZE_GADGET_ACCESS_PERMISSIONS);
    model.setModifiable(true);
    return model;
  }

  public static void broadcastEvent(ListenerService listenerService, String eventName, Object source, Object data) {
    try {
      listenerService.broadcast(eventName, source, data);
    } catch (Exception e) {
      LOG.warn("Error broadcasting event '" + eventName + "' using source '" + source + "' and data " + data, e);
    }
  }

}
