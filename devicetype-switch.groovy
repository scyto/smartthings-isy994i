/**
 *  ISY Controller
 *
 *  Copyright 2014 Richard L. Lynch <rich@richlynch.com>
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
def parse(String description) {
    printDebug "Parsing Dev ${device.deviceNetworkId} '${description}'"

    def parsedEvent = parseDiscoveryMessage(description)
    //printDebug "Parsed event: " + parsedEvent
    //printDebug "Body: " + parsedEvent['body']
    if (parsedEvent['body'] != null) {
        def xmlText = new String(parsedEvent.body.decodeBase64())
        //printDebug 'Device Type Decoded body: ' + xmlText

        def xmlTop = new XmlSlurper().parseText(xmlText)
        def nodes = xmlTop.node
        //printDebug 'Nodes: ' + nodes.size()

        def childMap = [:]
        parent.getAllChildDevices().each { child ->
            def childNodeAddr = child.getDataValue("nodeAddr")
            childMap[childNodeAddr] = child
        }

        nodes.each { node ->
            def nodeAddr = node.attributes().id
            def status = ''

            node.property.each { prop ->
                if (prop.attributes().id == 'ST') {
                    status = prop.attributes().value
                }
            }

            if (status != '' && childMap[nodeAddr]) {
                def child = childMap[nodeAddr]

                if (child.getDataValue("nodeAddr") == nodeAddr) {
                    def value = 'on'
                    if (status == '0') {
                        value = 'off'
                    }
                    try {
                        status = status.toFloat() * 99.0 / 255.0
                        status = status.toInteger()
                    } catch (NumberFormatException ex) {
                        printDebug "Exception parsing ${status}: ${ex} (will assume device is off)"
                        status = '0'
                        value = 'off'
                    }
                    printDebug "Updating ${child.label} ${nodeAddr} to ${value}/${status}"
                    child.sendEvent(name: 'switch', value: value)

                    if (status != 0) {
                        child.sendEvent(name: 'level', value: status)
                    }
                }
            }
        }
    }
}

private Integer convertHexToInt(hex) {
    Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    //convert IP/port
   // ip = convertHexToIP(ip)
   // port = convertHexToInt(port)
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

private def parseDiscoveryMessage(String description) {
    printDebug "parseSwitchDiscoveryMessage: "  + description
    def device = [:]
    def parts = description.split(',')
    parts.each { part ->
        part = part.trim()
        if (part.startsWith('headers')) {
            part -= "headers:"
            def valueString = part.trim()
            if (valueString) {
                device.headers = valueString
            }
        } else if (part.startsWith('body')) {
            part -= "body:"
            def valueString = part.trim()
            if (valueString) {
                device.body = valueString
            }
        }
    }

    device
}


// so we can turn debugging on and off
def printDebug(str)
{
    log.debug(str)
}