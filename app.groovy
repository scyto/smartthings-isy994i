    /**
    *  ISY Connect
    *
    *  Original Copyright 2014 Richard L. Lynch <rich@richlynch.com>
    *  Modified 2016 Mark S. Zachmann <mzachma@gmail.com> 
    *      Added support for dimmers and direct IP access without password
    *      This allows use of an NGINX or other simple web forwarder for internal LAN
    *      Improved reliability and timing of the light discovery process
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

    definition(
        name: "ISY Connect",
        namespace: "isy",
        author: "Mark Zachmann",
        description: "ISY Insteon Connection",
        category: "SmartThings Labs",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld@2x.png") {
    }


    preferences {
        // select IP or username,password
        page(name:"ipPage", title:"Use IP or ISY", content:"ipPage")
        // enter username/pwd
        page(name:"credPage", title:"ISY Setup 1", content:"credPage")
        // enter IP
        page(name:"isyPage", title:"ISY Setup 2", content:"isyPage")
        // read nodes to add
        page(name:"nodePage", title:"ISY Setup 3", content:"nodePage")
        // pick nodes to add
        page(name:"entryPage", title:"ISY Setup 4", content:"entryPage")
    }

    // Decide if we use IP proxy or standard ISY
    def ipPage() {
        printDebug "*****Running ip page"
        if(!state.hubid)
        {
            printDebug "initial hubid"
            state.hubid = 0
        }
        if(!state.ipaddress)
        {
            // as it turns out, this will execute only with a new install (or after uninstall)
            // since state is persistent
            printDebug "initial state ip address"
            state.ipaddress="192.168.0.92"
            state.port="80"
        }
        return dynamicPage(name:"ipPage", title:"ISY Setup Select", nextPage:"credPage", install:false, uninstall: true) {
            section("ISY Interface Selection") {
                paragraph "Intead of connecting directly to the ISY server, which requires sending username and password in plaintext you can use a very secure NGINX forwarder on a Raspberry PI to go to HTTPS."
                input( name:"isuseip", type:"bool", title: "Use a port forwarder (IP address)", required:"true", defaultValue:"true")
            }
        }
    }

    // Credentials preferences page - collect ISY username and password
    // or pick IP:port
    def credPage() {
        printDebug "*****Running cred page"
        if(isuseip)
        {
            return dynamicPage(name:"credPage", title:"ISY Setup Credentials (page 1 of 4)", nextPage:"nodePage", install:false) {
                section("Forwarder IP Selection") 
                {
                    paragraph "Enter the IP Address (xxx.xxx.xxx.xxx) and port for the forwarder. Set the PI to fixed IP or use a fixed DHCP."
                    input( name:"ipaddress", type:"text", title: "IP Address", required:"true", defaultValue:"192.168.0.92")
                    input( name:"ipport", type:"text", title: "Port", required:"true", defaultValue:"80")
                }
            }
        }
        else
        {
            return dynamicPage(name:"credPage", title:"ISY Setup Credentials (page 1 of 4)", nextPage:"isyPage", install:false) {
            section("ISY Authentication") 
                {
                    paragraph "For direct to an ISY server, enter the username and password."
                    input( name:"username", type:"text", title: "Username", required:"true", defaultValue:"admin")
                    input( name:"password", type:"password", title: "Password", required:"true")
                }
            }
        }
    }

    // ISY selection page - discover and choose which ISY to control
    // skipped if using ip address
    def isyPage() {
        printDebug "*****Running isy page"

        def refreshInterval = 5

        if(!state.subscribed) {
            printDebug('Subscribing to updates')
            // subscribe to answers from HUB, all responses will go here
            subscribe(location, "ssdpTerm.urn:udi-com:device:X_Insteon_Lighting_Device:1", ssdpHandler, [filterEvents:false])
            state.subscribed = true
        }

        printDebug('Performing discovery')
        sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:udi-com:device:X_Insteon_Lighting_Device:1", physicalgraph.device.Protocol.LAN))

        def devicesForDialog = getDevicesForDialog()

        return dynamicPage(name:"isyPage", title:"ISY Select Hub (page 2 of 4)", nextPage:"nodePage", install:false, refreshInterval:refreshInterval) {
            section("Select an ISY device...") {
                paragraph "Pick which ISY server to select devices from."
                input "selectedISY", "enum", required:false, title:"Select ISY \n(${devicesForDialog.size() ?: 0} found)", multiple:true, options:devicesForDialog
            }
        }
    }

    // Returns a map of ISYs for the preference page
    def getDevicesForDialog() {
        def devices = getDevices()
        def map = [:]
        devices.each {
            def value = it.value.ip
            def key = it.value.mac
            map["${key}"] = value
        }
        map
    }

    // Returns a map of ISYs for internal use
    def getDevices() {
        if (!state.devices) { state.devices = [:] }
        printDebug("There are ${state.devices.size()} devices at this time")
        state.devices
    }

    // Node selection preference page - choose which Insteon devices to control
    def nodePage() {
        printDebug "*****Running node page"

        def refreshInterval = 5;
        def path = "/rest/nodes"
        def nodes = getNodes()

        if(nodes.size() == 0)
        {
            if(state.subscribed)
            {
                unsubscribe()
                state.subscribed = false;
            }
        
            if(!state.subscribed) {
                printDebug('Subscribing to more updates')
                // subscribe to answers from HUB, all responses will go here
                subscribe(location, null, locationHandler, [filterEvents:false])
                state.subscribed = true
            }

            if(isuseip)
            {
                state.ipaddress = ipaddress
                state.port = ipport
            }
            else
            {
                def selDev = getSelectedDevice()
                state.ipaddress = selDev.value.ip
                state.port = selDev.value.port
            }
            sendHubCommand(getRequest(state.ipaddress, state.port, path))

            nodes = getNodes()
        }

        return dynamicPage(name:"nodePage", title:"ISY Node Reading (Page 3 of 4)", nextPage:"entryPage", install:false, refreshInterval:refreshInterval) {
            section("Waiting for nodes to be found") {
                paragraph "Wait for the next line to fill in with a set of non-zero nodes. Usually this will take 5 seconds. Then click Next at the top of the page."
                paragraph  "Finding Nodes... \n(${nodes.size() ?: 0} found)"
            }
        }

    }

    // Node selection preference page - choose which Insteon devices to control
    def entryPage() {
        printDebug "*****Running entry page"

        if(state.subscribed)
        {
            unsubscribe()
            state.subscribed = false;
        }

        def nodes = getNodes()
        def rooms = nodes.collect {it.value.name}.sort()

        // sort the nodes alphabetically ???
        return dynamicPage(name:"entryPage", title:"ISY Node Selection (Page 4 of 4)", nextPage:"", install:true, uninstall: true) {
            section("Select nodes...") {
                paragraph "Click below to get a list of devices (lights). Pick which lights to add to the SmartThings database. That will fill the list below. Then click Done to add them."
                input "selectedRooms", "enum", required:false, title:"Select Nodes \n(${nodes.size() ?: 0} found)", multiple:true, options:rooms
            }
        }
    }

    // Returns a map of Insteon nodes for the preferences page
    def getSelectedDevice() {
        def selDev
        selectedISY.each { dni ->
            def devices = getDevices()
            printDebug("Looking for ${dni}")
            selDev = devices.find { it.value.mac == dni }
        }
        selDev
    }

    // Returns a map of Insteon nodes for internal use
    def getNodes() {
        if (!state.nodes) {
            state.nodes = [:]
        }

        printDebug("There are ${state.nodes.size()} nodes at this time")
        state.nodes
    }

    // this updates all of the levels of all of the switches
    // we run this every 7 minutes (see schedule call in initialize())
    def sendStatusRequest()
    {
        if(!state.subscribed) 
        {
            printDebug('Scheduler has to resubscribe')
            // subscribe to answers from HUB, all responses will go here
            subscribe(location, null, locationHandler, [filterEvents:false])
            state.subscribed = true
        }
        def path = "/rest/status"
        sendHubCommand(getRequest(state.ipaddress, state.port, path))
    }

    // handle the response to a /rest/nodes request and enumerate all of the nodes
    def enumerateNodes(msg)
    {
        def xmlNodes = msg.xml.node
        printDebug 'Nodes: ' + xmlNodes.size()

        // here we clear things out for real since we are about to reread them
        state.nodes = [:]

        // parse the individual nodes from the rest result
        xmlNodes.each {
            def addr = it.address.text()
            def name = it.name.text()
            def type = it.@nodeDefId.text()
            if(addr && name && type)
            {
                printDebug "${addr} => ${name}.${type}"
                state.nodes[addr] = [name:name, type:type, level:'0', ison:false]
            }
        }        
    }

    // update a switch settings so it looks correct
    def updateNodeLevel(d, level)
    {
        def nodes = getNodes()
        def nodeAddress = d.device.deviceNetworkId - 'isy.'
        def n = nodes?.find {
            it.key == nodeAddress
        }
        def isOn = (level != '0')
        // check the cached value so we do not thrash
        if(!n || (n.value.ison != isOn) || (isOn && n.value.level != level))
        {
            if(n)
                printDebug 'sending event to kid: '+d.device.deviceNetworkId+"@"+level+". Old level=" + n.value.level
             else  {
                printDebug 'sending event to kid: '+d.device.deviceNetworkId+"@"+level+". No existing node"
             }
            if(!isOn)  {
                // tell the switch its off but do not set level value
                d.sendEvent(name: 'switch', value: 'off')
            }
            else  {
                // tell the switch it is on, and set the current level value
                d.sendEvent(name: 'level', value: level)
                d.sendEvent(name: 'switch', value: 'on')
            }

            // update the cached node value
            if(n) {
                n.value.ison = isOn
                if(isOn)
                    n.value.level = level
            }
        }
        else  {
            printDebug "No change to kid: " + nodeAddress
        }
    }

    // deal with the result from a single switch status response
    def evaluateSwitchStatus(nodeAddress, msg)
    {
        // a single status request
        printDebug "node address="+nodeAddress
        printDebug "found status request"+msg.body
        def xmlNodes = msg.xml.property
        def level = '0'
        xmlNodes.each {
            level = it.@formatted.text() - '%'
            printDebug "Level: ${level}"
        }
        // now set the level for this switch
        // get all child devices
        def kids = getChildDevices()    // do not get virtual devices (?)
        def dni = 'isy.'+nodeAddress    // the dni we are using
        def d = kids?.find {
            it.device.deviceNetworkId == dni
        }
        if(d)
        {
            updateNodeLevel(d, level)
        }
    }

    def evaluateAllStatus(msg)
    {
        // a single status request
        // printDebug "found all status request"+msg.body
        def xmlNodes = msg.xml.node
        // get all child devices
        def kids = getChildDevices()    // do not get virtual devices (?)
        xmlNodes.each {
        // now set the level for this switch
            def level = it.property.@formatted.text() - '%'
            def nodeAddress = it.@id
            // printDebug "address.Level: ${nodeAddress}.${level}"
            def dni = 'isy.'+nodeAddress    // the dni we are using
            def d = kids?.find {
                it.device.deviceNetworkId == dni
            }
            if(d)
            {
                updateNodeLevel(d, level)
            }
        }
    }

    // Handle rest response from the ISY forwarder
    def locationHandler(evt) {
        if(evt.name == "ping") {
            return ""
        }

        def description = evt.description
        def hub = evt?.hubId
        state.hubid = hub

        def msg = parseLanMessage(evt.description)

        printDebug('lan message headers: ' + msg.header)

        // the SourceUrl property is echoed from our NGINX server only!
        def sourceUrl = '/rest/nodes'
        if(msg.headers['SourceUrl'])
        {
            printDebug('lan source header: ' + msg.headers.SourceUrl)
            sourceUrl = msg.headers.SourceUrl
        }

        if (msg.body != null) 
        {
            def statusReq = '/rest/status'

            // ------- /rest/nodes
           if('/rest/nodes' == sourceUrl)
            {
                enumerateNodes(msg)
            }
            else if(statusReq == sourceUrl.take(statusReq.size()))
            {
                // ------- /rest/status/xx xx xx xx
               if(statusReq.size() < sourceUrl.size())
                {
                    // a single status request
                    def nodeAddress = sourceUrl - statusReq - '/'   // remove the leadin
                    evaluateSwitchStatus(nodeAddress, msg)
                }
                // ------- /rest/status
                else
                {
                    evaluateAllStatus(msg)
                }
            }
        }
    }

    // Handle discovery answers from ISYs (via the ST hub)
    def ssdpHandler(evt) {
        if(evt.name == "ping") {
            return ""
        }

        printDebug('Received ssdp Response: ' + evt.description)

        def description = evt.description
        def hub = evt?.hubId
        def parsedEvent = parseDiscoveryMessage(description)
        parsedEvent << ["hub":hub]

        if (parsedEvent?.ssdpTerm?.contains("udi-com:device:X_Insteon_Lighting_Device:1")) {
            def devices = getDevices()
            printDebug("got devices list with ${devices.size()} devices")
            if (!(devices."${parsedEvent.ssdpUSN.toString()}")) { //if it doesn't already exist
                printDebug('Parsed Event: ' + parsedEvent)
                devices << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
            } else { // just update the values
                printDebug('updating values')
                def d = devices."${parsedEvent.ssdpUSN.toString()}"
                boolean deviceChangedValues = false
                printDebug("device port=${parsedEvent.port}, ip=${parsedEvent.ip}")
                def intIp = convertHexToIP(parsedEvent.ip)
                def intPort = convertHexToInt(parsedEvent.port)
                if(d.ip != intIp || d.port != intPort) 
                {
                    d.ip = intIp
                    d.port = intPort
                    deviceChangedValues = true
                    printDebug("Changed ip:port to: ${intIp}:${intPort}")
                }

                if (deviceChangedValues) 
                {
                    def children = getAllChildDevices()
                    children.each {
                        if (it.getDeviceDataByName("mac") == parsedEvent.mac) {
                            //it.subscribe(parsedEvent.ip, parsedEvent.port)
                        }
                    }
                }
            }
        }
        else
        {
            printDebug "unknown event" + parsedEvent;
        }
    }

    // Called after the last preferences page is completed
    def installed() {
        printDebug("!!!!! running installed")
        // remove location subscription
        unsubscribe()
        state.subscribed = false

        printDebug "Installed with settings: ${settings}"

        initialize()
    }

    def updated() {
        printDebug("!!!!! running updated")
        printDebug "Updated with settings: ${settings}"

        unsubscribe()
        initialize()
    }

    // One node needs to be selected to receive all the messages from the ISY
    // This returns its Insteon address.
    def getPrimaryNode(curNode) {
        if (!state.primaryNode) {
            state.primaryNode = curNode
        }
        return state.primaryNode
    }

    def initialize() {
        printDebug("!!!!! running initialize")
        if(isuseip)
        {
            initializeIfIp()
        }
        else
        {
            initializeIfDev()
        }

        // we need to have a subscription to get device change messages from the device REST requests
        if(!state.subscribed) {
            printDebug('Subscribing to additional updates')
            // subscribe to answers from HUB, all responses will go here
            subscribe(location, null, locationHandler, [filterEvents:false])
            state.subscribed = true
        }

        // send the first status request, then every 7 minutes using Cron
        sendStatusRequest()
        unschedule()    // i think we need to call this to avoid running multiple schedules if we rerun this
        schedule("0 0/7 * 1/1 * ? *", sendStatusRequest)
    }

    def initializeIfDev() {
        printDebug('Initializing for devices')

        def selDev = getSelectedDevice()
        def nodes = getNodes()

        if (selDev) {
            def kids = getAllChildDevices()
            selectedRooms.each { room ->
                def nodeAddr = nodes.find { it.value.name == room }.key
                def dni

                /* First decide on the device network ID - assign one device
                the ISY address and give the other devices unique addresses */
                if (getPrimaryNode(nodeAddr) == nodeAddr) {
                    // This device will receive all the updates from the ISY, and
                    // will relay the updates to the other devices.
                    dni = selDev.value.mac
                }
                else {
                    // These devices will not directly receive any updates - the
                    // primary node will have to relay updates.
                    dni = selDev.value.mac + ':' + nodeAddr
                }

                def d = kids?.find {
                    it.device.deviceNetworkId == dni
                }

                if (!d) {
                    printDebug("Adding node ${nodeAddr} as ${dni}: ${nodes[nodeAddr]}")
                    def atype = nodes[nodeAddr].type
                    def childType = 'ISY Switch'
                    if(atype.contains('immer'))
                    {
                        childType = 'ISY Dimmer'
                        printDebug("dimmer found for ${nodes[nodeAddr]}")
                    }
                    d = addChildDevice("isy", childType, dni, selDev?.value.hub, [
                        "label": nodes[nodeAddr].name,
                        "data": [
                            "nodeAddr": nodeAddr,
                            "ip": selDev.value.ip,
                            "port": selDev.value.port,
                            "username": username,
                            "password": password,
                            "switch" : 'off',
                            "level" : '50'
                        ]
                    ])
                }
            }
        }
    }

    def initializeIfIp() {
        printDebug('Initializing for IP')

        def nodes = getNodes()
        def kids = getAllChildDevices()

        selectedRooms.each { room ->
            def nodeAddr = nodes.find { it.value.name == room }.key
            def dni = 'isy.'+nodeAddr

            def d = kids?.find {
                    it.device.deviceNetworkId == dni
                    }

            if (!d) {
                printDebug("Adding node ${nodeAddr} as ${dni}: ${nodes[nodeAddr]}")
                def atype = nodes[nodeAddr].type
                def childType = 'ISY Switch'
                if(atype.contains('immer'))
                {
                    childType = 'ISY Dimmer'
                    printDebug("dimmer found for ${nodes[nodeAddr]}")
                }
                d = addChildDevice("isy", childType, dni, state.hubid, [
                    "label": nodes[nodeAddr].name,
                    "data": [
                        "nodeAddr": nodeAddr,
                        "ip": ipaddress,
                        "port": ipport,
                        "username": "",
                        "password": ""
                    ]
                ])
            }
        }
    }

    // Parse the various headers the ST hub adds into a map
    private def parseDiscoveryMessage(String description) {
        printDebug "Parse discovery message " + description

        def device = [port:'0050']
        def parts = description.split(',')
        parts.each { part ->
            part = part.trim()
            if (part.startsWith('devicetype:')) {
                def valueString = part.split(":")[1].trim()
                device.devicetype = valueString
            } else if (part.startsWith('mac:')) {
                def valueString = part.split(":")[1].trim()
                if (valueString) {
                    device.mac = valueString
                }
            } else if (part.startsWith('networkAddress:')) {
                def valueString = part.split(":")[1].trim()
                if (valueString) {
                    device.ip = valueString
                }
            } else if (part.startsWith('deviceAddress:')) {
                def valueString = part.split(":")[1].trim()
                if (valueString) {
                    printDebug("reading device port ${valueString}")
                    device.port = valueString
                }
            } else if (part.startsWith('ssdpPath:')) {
                def valueString = part.split(":")[1].trim()
                if (valueString) {
                    device.ssdpPath = valueString
                }
            } else if (part.startsWith('ssdpUSN:')) {
                part -= "ssdpUSN:"
                def valueString = part.trim()
                if (valueString) {
                    device.ssdpUSN = valueString
                }
            } else if (part.startsWith('ssdpTerm:')) {
                part -= "ssdpTerm:"
                def valueString = part.trim()
                if (valueString) {
                    device.ssdpTerm = valueString
                }
            } else if (part.startsWith('headers')) {
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

    // Helper function to convert hex number to integer
    private Integer convertHexToInt(hex) {
        Integer.parseInt(hex,16)
    }

    // Helper function to convert hex IP address into decimal dotted quad format
    private String convertHexToIP(hex) {
        [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
    }

    // Get decimal ip:port from hex ip and hex port
    private getHostAddress(ip, port) {
        printDebug "Using ip: ${ip} and port: ${port}"
        return ip + ":" + port
    }

    private getAuthorization() {
        def userpassascii = username + ":" + password
        "Basic " + userpassascii.encodeAsBase64().toString()
    }

    // Perform an HTTP GET request to the specified ip, port, and URL path
    // Response will be received async in locationHandler assuming no devices
    // have been created yet.
    def getRequest(ip, port, path) {
        new physicalgraph.device.HubAction(
            'method': 'GET',
            'path': path,
            'headers': [
                'HOST': getHostAddress(ip, port),
                'Authorization': getAuthorization()
            ], null)
    }

    // so we can turn debugging on and off
    def printDebug(str)
    {
        log.debug(str)
    }

