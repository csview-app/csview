```bash
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file  \
    -Dfile=lib/swt-4.22-cocoa-macosx-aarch64/swt.jar \
    -DgroupId=org.eclipse.platform -DartifactId=org.eclipse.swt.cocoa.macosx.aarch64 \
    -Dversion=4.22 -Dpackaging=jar \
    -DlocalRepositoryPath=./local-maven-repo
```