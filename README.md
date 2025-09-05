# Morpheus Omega Test Plugin

![](src/assets/images/omega.svg)

# Purpose
A plugin providing examples for reference and test validation.

## Overview
The main purpose of the Omega Test Plugin is to provide a way for developers to make & validate changes to the API without needing a real environment, additionally for QA to test our plugin API changes end to end using the plugin. It also serves as an example for authors of “real” plugins to see how they should utilize APIs.

To that end additional effort has been made to write useful comments to help understand how things fit together or are meant to be used, reducing friction for plugin developers. 

# Building 
Build with `./gradlew shadowjar`

# Installing 
The plugin jar located at `/morpheus-omega-test-plugin/build/libs/morpheus-omega-test-plugin-0.3.0-SNAPSHOT-all.jar` 

Install to your morpheus appliance by navigating to the **Administration > Integrations > Plugins** UI and clicking the **Add +** button. 

Browse by Clicking 'Add File' or Drop the jar in and click upload to activate the plugin. 
