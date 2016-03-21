Wings
====

[![Build Status](http://img.shields.io/travis/groundupworks/wings.svg?style=flat-square)](https://travis-ci.org/groundupworks/wings)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Maven Central](https://img.shields.io/maven-central/v/com.groundupworks.wings/wings.svg?style=flat-square)]()

Wings is an Android library for silently sharing photos to an extensible set of endpoints. The currently supported endpoints include:

* Dropbox
* Facebook
* Google Cloud Print

Linking user accounts, selecting destinations, as well as the retry logic to ensure delivery are all handled by Wings. The application integrates with Wings APIs that are non-specific to the underlying endpoints, and Wings will take care of the rest. Wings also defines an interface for the application to add additional endpoints, so consider the above list easily extensible.

Wings started off as a component in [Flying PhotoBooth](https://play.google.com/store/apps/details?id=com.groundupworks.flyingphotobooth) and [Party PhotoBooth](https://play.google.com/store/apps/details?id=com.groundupworks.partyphotobooth), and has helped us publish photos reliably and efficiently over the years. We are now open-sourcing it because posting a photo should be easy, while integrating with native SDKs of the ever-growing number of service providers is anything but easy... so we hope Wings will take care of that part for you, and should you find yourself building new endpoints, make a pull request and add to the list!

Refer to the [Project Site](http://groundupworks.github.io/wings) to get started. Or if you already know what you are doing, go right ahead:

```
compile 'com.groundupworks.wings:wings:1.0.4'
compile 'com.groundupworks.wings:wings-dropbox:1.0.4'  // For Dropbox
compile 'com.groundupworks.wings:wings-facebook:1.0.4' // For Facebook
compile 'com.groundupworks.wings:wings-gcp:1.0.4'      // For Google Drive or Cloud Print
```
