How the cftlib works

The cftlib uses isolated classloaders to run multiple spring boot applications in a single Java Virtual Machine (JVM).

Isolated classloaders allow different versions of the same class to coexist within the same JVM, since the JVM identifies a type by its name, package and classloader.


Project overview

lib/bootstrapper

Handles the cftlib bootstrapping process; an application that starts other applications within the same JVM.

lib/injected

Added to the classpath of each spring boot application that the cftlib runs, enabling us to inject new & custom functionality.

For example, this library contains a Spring boot event listener that we use to coordinate the bootstrapping process.

lib/runtime

A minimal spring boot application that provides the CftLibApi implementation and s2s simulator.

lib/test-runner

Provides integration testing functionality for 

Previous ideas:

* Run everything as a single Spring boot Application

Rather than running each cft service as an independent spring boot application, run a single spring boot application containing all the application code.

This falls down on the shared classpath; irreconcilable dependency conflicts can arise when two or more services share a dependency for which no mutually compatible version exists.
I encountered this with the Jackson library when prototyping this idea; one CFT service would only work with jackson version X and another with version Y, so they couldn't run under a single classloader where you can have only one version of jackson loaded.

pros: 
* Significant further reduction in resource requirements
* Faster boot times
cons: 
 dependency conflicts (terminal)
 colliding URLs; two different services might define the same URL mappings

* Extract the application fat jars from the docker images published by the CNP pipeline

Rather than assembling the cft application classpaths using Gradle's dependency resolution, copy and run the complete fat jars from the docker images in the hmcts container registries.

cons:
 * Transient images - HMCTS container images are cleared down after a time
 * Classpath injection may be harder
