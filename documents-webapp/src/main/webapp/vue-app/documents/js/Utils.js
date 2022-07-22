function getFolderPath(path){
  if (eXo.env.portal.spaceName){
    const index = path.indexOf('/Documents');
    if (index !== -1){
      return path.substring(index+10);
    }
  } else {
    if (path.includes('/Private')){
      const index  = path.indexOf('/Private');
      if (index !== -1){
        return path.substring(index);
              
      }
    }
    if (path.includes('/Public')){
      const index = path.indexOf('/Public');
      if (index !== -1){
        return path.substring(index);
      }
    } 
  }
}
export function getFolderpreviewPathFrombreadCrumb(breadCrumb){

  for (const folderName in breadCrumb){
    const portalBaseURL= breadCrumb[folderName].slice(0,breadCrumb[folderName].indexOf('?'));
    const path = getFolderPath(decodeURIComponent(breadCrumb[folderName].slice(breadCrumb[folderName].indexOf('?')+1,breadCrumb[folderName].indexOf('&'))));
    const pathFolder = `${portalBaseURL}${path}`;
    breadCrumb[folderName]=pathFolder ;
  }
  return breadCrumb;
}

