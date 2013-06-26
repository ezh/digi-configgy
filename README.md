Digi-Configgy [![Build Status](https://travis-ci.org/ezh/digi-configgy.png?branch=master)](https://travis-ci.org/ezh/digi-configgy) [![Coverage Status](https://coveralls.io/repos/ezh/digi-configgy/badge.png?branch=master)](https://coveralls.io/r/ezh/digi-configgy?branch=master)
=============

__A library for handling configurations__

[Documentation](https://github.com/twitter/ostrich)

If you want to improve it, please send mail to sbt-android-mill at digimead.org. You will be added to the group. Please, feel free to add yourself to authors.

This library was created from original works by Robey Pointer and originally posted @ <https://github.com/robey/configgy>.

Original Configgy library was deprecated by [Ostrich](https://github.com/twitter/ostrich). But Ostrich serialization mechanism for saving configuration files is still broken. Also original Configgy contain few bright ideas.

### Setup

Add Maven or Ivy repository:

```scala
resolvers += "digimead-maven" at "http://storage.googleapis.com/maven.repository.digimead.org/"
```

```scala
resolvers += Resolver.url("digimead-ivy", url("http://storage.googleapis.com/ivy.repository.digimead.org/"))(Resolver.defaultIvyPatterns)
```

Add dependency:

```scala
libraryDependencies += "org.digimead" %% "digiconfiggy" % "VERSION"
```

## Target platform

* Scala 2.10.2 (request for more if needed)
* JVM 1.6+
* The only 3rd-party library dependency is [SLF4J](http://www.slf4j.org/)

## Participate in the development ##

Branches:

* origin/master reflects a production-ready state
* origin/release-* support preparation of a new production release. Allow for last-minute dotting of i’s and crossing t’s
* origin/hotfix-* support preparation of a new unplanned production release
* origin/develop reflects a state with the latest delivered development changes for the next release (nightly builds)
* origin/feature-* new features for the upcoming or a distant future release

Structure of branches follow strategy of http://nvie.com/posts/a-successful-git-branching-model/

If you will create new origin/feature-* please open feature request for yourself.

* Anyone may comment you feature here.
* We will have a history for feature and ground for documentation
* If week passed and there wasn't any activity + all tests passed = release a new version ;-)

AUTHORS
-------

* Robey Pointer (original Configgy)
* Alexey Aksenov

LICENSE
-------

The Digi-Configgy is licensed to you under the terms of
the Apache License, version 2.0, a copy of which has been
included in the LICENSE file.

Copyright
---------

Copyright © 2012-2013 Alexey B. Aksenov/Ezh.

Copyright © 2009-2010 Robey Pointer.

All rights reserved.
