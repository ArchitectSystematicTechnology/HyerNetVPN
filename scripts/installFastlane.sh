#!/bin/bash

## --- System Dependencies for fastlane
apt-get update -qq && \
apt-get -y dist-upgrade && \
apt-get -y install make build-essential ruby ruby-dev imagemagick

gem install fastlane

## ------------------------------------------------------
## --- Android Emulator 
#
#echo y | sdkmanager "system-images;android-27;google_apis;x86_64"
# Install Android SDK emulator package
echo y | sdkmanager "emulator"

echo y | sdkmanager "system-images;android-25;google_apis;x86_64"
#
#echo no | avdmanager create avd --force --name testApi31 --abi google_apis/x86_64 --package 'system-images;android-31;google_apis;x86_64'
#echo no | avdmanager create avd --force --name testApiduet --abi google_apis/x86_64 --package 'system-images;android-31;google_apis;x86_64'
  
echo no | avdmanager create avd --force --name testApi27 --abi google_apis/x86_64 --package 'system-images;android-25;google_apis;x86_64'
echo no | avdmanager create avd --force --name testApiduet --abi google_apis/x86_64 --package 'system-images;android-25;google_apis;x86_64'

#echo no | avdmanager create avd --force --name testApi27 --abi google_apis/x86_64 --package 'system-images;android-27;google_apis;x86_64'
#echo no | avdmanager create avd --force --name testApiduet --abi google_apis/x86_64 --package 'system-images;android-27;google_apis;x86_64'

#
#
##bundle exec fastlane android bitmask_screenshots
#
