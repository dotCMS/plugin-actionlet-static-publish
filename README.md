
README
------

This bundle plugin will provide a workflow actionlet called "Static Publish" that can be added to any workflow process.  It allows you to statically publish your content, page or file to your server's filesystem.   You configure this plugin using the plugin.properties file here:
/dotCMS/plugin-actionlet-static-publish/blob/master/src/main/resources/plugin.properties

the 
STATIC_PUBLISH_FOLDER value is the root folder to which your pages/content will be statically published.  For security, it MUST point to folder that is under your WEBROOT.  The variables $hostname and $language can be used in the STATIC_PUBLISH_FOLDER you specify and will be replaced by the values in the contentlet, e.g.

```
/opt/dotcms/tomcat8/webapps/ROOT/static/$hostname/$language
```
 would be expanded to
```
/opt/dotcms/tomcat8/webapps/ROOT/static/demo.dotcms.com/en
```




How to build this plugin
-------------------------

To install all you need to do is build the JAR. to do this run 
./gradlew jar
This will build a jar in the build/libs directory

1. To install this bundle:

Copy the bundle jar file inside the Felix OSGI container (dotCMS/felix/load).
        OR
Upload the bundle jar file using the dotCMS UI (CMS Admin->Dynamic Plugins->Upload Plugin).
	
2. To uninstall this bundle:

Remove the bundle jar file from the Felix OSGI container (dotCMS/felix/load).
        OR
Undeploy the bundle using the dotCMS UI (CMS Admin->Dynamic Plugins->Undeploy).

