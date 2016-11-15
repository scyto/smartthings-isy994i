# smartthings-isy994i
Allows a SmartThings hub to control Insteon devices through an ISY-994i

# Usage

1) Go to https://graph.api.smartthings.com/ and add the app under "My SmartApps" and the device types under "My Device Types". Publish both for yourself.

2) Open the SmartThings app, click on the Marketplace icon in the bottom right corner.

3) Select the SmartApps tab, choose My Apps from the bottom, and choose ISY Connect.

4) On the first page, select whether to use a forwarder or go straight to an ISY-994i. 
My forwarder is a RaspberryPI running NGINX. NGINX is set so that a standard HTTP request on port 80 converts to an HTTPS request to the ISY-994i. This is far more secure than going direct to the ISY device and doesn't keep your password around in plaintext. 

5) On the second page, if you picked a forwarder, enter the IP address and port on the next page. If you picked an ISY-994i direct, enter the credentials on the next page. Click Next.

5) If you are going direct to ISY-994i the next page lets you select the device. Select it and then click Next in the top right corner.

6) On the next preferences page, wait for a few seconds and the page will populate with a count. When finished just click Next.

7) Finally, click to select which Insteon devices you'd like to control. Click Done in the top right corner.

