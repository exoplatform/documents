export function getFolderpreviewPathFrombreadCrumb(breadCrumb){
  let ParentpathFolder ='';
  let isSource = true;
  for (const folderName in breadCrumb){
    const portalBaseURL= breadCrumb[folderName].slice(0,breadCrumb[folderName].indexOf('?'));
    const pathFolder = `${portalBaseURL}/${ParentpathFolder}${folderName}`;
    breadCrumb[folderName]=pathFolder ;
    ParentpathFolder = `${folderName}/` ;
    if (isSource){
      breadCrumb[folderName]= portalBaseURL;
      ParentpathFolder = '';
      isSource= false ;
    }
  }
  return breadCrumb;
}