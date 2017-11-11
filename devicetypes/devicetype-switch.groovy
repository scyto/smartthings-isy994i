/**
 *  ISY Controller
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
    definition (name: "ISY Switch", namespace: "isy", author: "Richard L. Lynch") {
        capability "Switch"
        capability "Polling"
        capability "Refresh"
        capability "Actuator"
    }

    simulator {
    }

    tiles {
        standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"off"
            state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"on"
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
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
    printDebug "Sending request for ${path} from ${device.deviceNetworkId}"

    new physicalgraph.device.HubAction(
        'method': 'GET',
        'path': path,
        'headers': [
            'HOST': getHostAddress(),
            'Authorization': getAuthorization()
        ])
}

// handle commands
def on() {
    printDebug "Executing 'on'"

    sendEvent(name: 'switch', value: 'on')
    def node = getDataValue("nodeAddr").replaceAll(" ", "%20")
    def path = "/rest/nodes/${node}/cmd/DON"
    getRequest(path)
}

def off() {
    printDebug "Executing 'off'"

    sendEvent(name: 'switch', value: 'off')
    def node = getDataValue("nodeAddr").replaceAll(" ", "%20")
    def path = "/rest/nodes/${node}/cmd/DOF"
    getRequest(path)
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
    getRequest(path)
}

// so we can turn debugging on and off
def printDebug(str)
{
 //   log.debug(str)
}
