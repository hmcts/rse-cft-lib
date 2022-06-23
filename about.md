How the cftlib works


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

