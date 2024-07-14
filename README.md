Plugin app for Tasker and MacroDroid to integrate with [Meari](https://www.meari.com/about-us/) battery cameras
So far tested with cameras sold under the following brands:
* CloudEdge
* [ieGeek](https://amzn.to/3y7KxYG)
* [FOAOOD](https://amzn.to/3Lk7KtX)
* [IHOXTX](https://amzn.to/3xYseVY)

License: freely shared but still uncertain, see LICENSE for details 

V 1.5.0 - Initial public release

Features:
* Actions:
    * enable all cameras: they will send notifications and do any action you configured on them
    * disable all cameras: they will not be triggered by movement and won't send notifications
    * enable alarm on all cameras: enable the alarm feature on all the cameras. Depending on how you configured each camera this might mean a siren or just some lights
    * disable alarm on all cameras
* Events/Triggers
    * Camera detection: will trigger the event when one of the enabled cameras has seen movement

The detection settings for each camera must/may still be configured individually, so that some cameras will have high detection threasolds and detect only humans, whereas others might trigger on any movement detected from the PIR sensor

Ideas for future improvements:
* remove all the unused example code from Meari SDK
* allow taking an high-res picture from a camera
* allow forcing the alarm sound to trigger on all the camera (e.g. distributed Alarm Siren)
* allow enabling/disabling selectively a single camera

**Security Caveat**:
* This plugin stores your credentials to CloudEdge, even if encrypted: this is a known limitation

IMPORTANT:
* you MUST setup a SECONDARY CouldEdge account to use this plugin
  * create a new account in the CloudEdge cloud
  * share with that account the cameras you want to control via this plugin
  * otherwise it WILL NOT WORK
* this plugin works only CloudEdge app
  * reset any camera from other compatible-brands and register them within the CloudEdge App

Why?: because CloudEdge does not allow you to have the same user logged in from multiple device, so you need a dedicated user for this plugin, otherwise the first time you open the app this plugin will stop working


HOW-TO use it:
* register your cameras within the [CloudEdge app](https://play.google.com/store/apps/details?id=com.cloudedge.smarteye&hl=en) (even if from other compatible brands)
* share the cameras with your SECONDARY account (see above)
* install the APK (you can download it from the GitHub releases)
* start it: so that it's registered and available to Tasker/Macrodroid
* login with your SECONDAY (see above) CloudEdge account, you need to enter
  * Your contry code: e.g. US
  * Your country prefix: e.g. 1  (e.g. the initial part after the +)
  * your username/mail: e.g. example.user@whatevermail.you.use.com
  * your password
* within Tasker/Macrodroid
  * go to the task you want to use
  * add action > external app > CloudEdge4Tasker > Enable all cameras: this will enable movement detection on all cameras

Example use cases for me:
* Disable movement detection when at home: I don't want alarms and this spares battery
* Enable movement detection when leaving home or during bedtime: so that each camera will apply it's detection settings and will raise alarms
* Receive alarms, pass them thru [HumanDetaction4Tasker](https://github.com/SimoneAvogadro/HumanDetection4Tasker) and if there's a person play an alarm in the "Alarm" audio channel (which will play even if the phone is muted during bedtime) 
