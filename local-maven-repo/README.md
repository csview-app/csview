```bash
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  \
    -Dfile=$HOME/Downloads/swt-4.22-cocoa-macosx-aarch64/swt.jar \
    -Dsources=$HOME/Downloads/swt-4.22-cocoa-macosx-aarch64/src.zip \
    -DgroupId=org.eclipse.platform -DartifactId=org.eclipse.swt.cocoa.macosx.aarch64 \
    -Dversion=4.22 -Dpackaging=jar \
    -DlocalRepositoryPath=./local-maven-repo
    
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  \
    -Dfile=$HOME/Downloads/swt-4.22-win32-win32-x86_64/swt.jar \
    -Dsources=$HOME/Downloads/swt-4.22-win32-win32-x86_64/src.zip \
    -DgroupId=org.eclipse.platform -DartifactId=org.eclipse.swt.win32.win32.x86_64 \
    -Dversion=4.22 -Dpackaging=jar \
    -DlocalRepositoryPath=./local-maven-repo
    
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  \
    -Dfile=$HOME/Downloads/swt-4.22-gtk-linux-x86_64/swt.jar \
    -Dsources=$HOME/Downloads/swt-4.22-gtk-linux-x86_64/src.zip \
    -DgroupId=org.eclipse.platform -DartifactId=org.eclipse.swt.gtk.linux.x86_64 \
    -Dversion=4.22 -Dpackaging=jar \
    -DlocalRepositoryPath=./local-maven-repo
```