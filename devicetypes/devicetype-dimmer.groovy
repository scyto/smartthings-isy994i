/**
 *  ISY Controller - Dimmer devicetype
 *
 *  Original Copyright 2014 Richard L. Lynch <rich@richlynch.com>
 *  Heavily modified by Mark Zachmann 2016
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
    definition (name: "ISY Dimmer", namespace: "isy", author: "Richard L. Lynch") {
        capability "Switch"
        capability "Switch Level"
        capability "Polling"
        capability "Refresh"
        capability "Actuator"
    }

    simulator {
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#79b821", nextState:"off"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"on"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "switch"
        details (["switch", "refresh"])
    }
}

// parse events into attributes
// this is never called because the hub gets the REST responses
def parse(String description) {
    printDebug "Parsing Dev ${device.deviceNetworkId} '${description}'"
}

private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")
    printDebug "Using ip: ${ip} and port: ${port} for device: ${device.id}"
    return ip + ":" + port
}

private getAuthorization() {
    def userpassascii = getDataValue("username") + ":" + getDataValue("password")
    "Basic " + userpassascii.encodeAsBase64().toString()
}

def getRequest(path) {
    printDebug "Sending a request for ${path} from ${device.deviceNetworkId}"
    def headers = [
        'HOST': getHostAddress(),
        'Authorization': getAuthorization()
        ]

    return new physicalgraph.device.HubAction(
        'method': 'GET',
        'path': path,
        'headers': headers)
}

// handle commands
def on() {
    def level = device.latestValue('level')
    printDebug "Executing 'on' ${level}"
    if(!level)
    {
        level = '50'
    }

    level = level.toFloat() * 255.0 / 99.0
    level = level.toInteger()
    sendEvent(name: 'switch', value: 'on')
    def node = getDataValue("nodeAddr").replaceAll(" ", "%20")
    def path = "/rest/nodes/${node}/cmd/DON/${level}"
    return getRequest(path)
}

def off() {
    printDebug "Executing 'off'"

    sendEvent(name: 'switch', value: 'off')
    def node = getDataValue("nodeAddr").replaceAll(" ", "%20")
    def path = "/rest/nodes/${node}/cmd/DOF"
    return getRequest(path)
}

def setLevel(value) {
    printDebug "Executing dim ${value}"

    sendEvent(name: 'switch', value: 'on')
    sendEvent(name: 'level', value: value)

    value = value.toFloat() * 255.0 / 99.0
    value = value.toInteger()
    if (value > 255) {
        value = 255
    }
    def node = getDataValue("nodeAddr").replaceAll(" ", "%20")
    def path = "/rest/nodes/${node}/set/DON/${value}"
    return getRequest(path)
}

def poll() {
    if (!device.deviceNetworkId.contains(':')) {
        printDebug "Executing 'poll' from ${device.deviceNetworkId}"
        refresh()
    }
    else {
        printDebug "Ignoring poll request for ${device.deviceNetworkId}"
    }
}

def refresh() {
    printDebug "Executing 'refresh'"
    def node = getDataValue("nodeAddr").replaceAll(" ", "%20")
    def path = "/rest/status/${node}"
    return getRequest(path)
}

// so we can turn debugging on and off
def printDebug(str)
{
 //   log.debug(str)
}
