## HTTPBuilder = Easy HTTP client for Groovy.

Build Instructions:

 HTTPBuilder is built using Gradle (https://gradle.org/)
 
 $ ./gradlew publishToMavenLocal

Documentation: https://github.com/jgritman/httpbuilder/wiki

### Installation
Add dependency to your build.gradle

```
repositories {
  ...
  maven { url "https://jitpack.io" }
}

dependencies {
    compile 'com.github.jgritman:httpbuilder:1.0-M1-snapshot'
}
```

In addition if you don't want to use jitpack.io then use following github package registry:

```groovy
repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jgritman/httpbuilder")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    
    dependencies {
        compile 'org.codehaus.groovy.modules.http-builder:http-builder:1.0-M1-snapshot'
    }

```


### Contributions:

 This project relies on the work of many open source projects including:
  * Groovy: http://groovy-lang.org
  * Apache HttpClient: http://hc.apache.org
  * Json-Lib: http://json-lib.sourceforge.net/
  * Neko HTML: http://nekohtml.sourceforge.net/
  * Signpost: http://code.google.com/p/oauth-signpost/

 This project also includes source code written by Martin Blom (martin@blom.org)
 in the 'thirdparty' package.  It is licensed under the LGPL v3 and
 re-distributed with permission from the author.


### License:

 HTTPBuilder is copyright 2009-2011 Thomas Nichols except where otherwise noted.

 This project is licensed under the Apache License Version 2.0 except where
 otherwise noted in the source files.

 You are receiving this code free of charge, which represents many hours of
 effort from other individuals and corporations.  As a responsible member
 of the community, you are encouraged (but not required) to donate any
 enhancements or improvements back to the community under a similar open
 source license.  Thank you. -TMN


