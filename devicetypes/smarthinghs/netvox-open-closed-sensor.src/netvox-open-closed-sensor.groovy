/**
 *  SmartSense Open/Closed Sensor
 *
 *  Copyright 2014 SmartThings
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
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus

metadata {
	definition(name: "Netvox Open/Closed Sensor", namespace: "smarthinghs", author: "SmartThings") {
		capability "Battery"
		capability "Configuration"
		capability "Contact Sensor"
		capability "Refresh"
		capability "Health Check"
		capability "Sensor"
      //  capability "Voltage Measurement"

		command "enrollResponse"


		fingerprint inClusters: "0000,0001,0003,0015,0500,0020,0B05", outClusters: "0019", manufacturer: "netvox", model: "Z311WE3ED"
		
	}

	simulator {

	}

	preferences {
		input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
		input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
	}

	tiles(scale: 2) {
		multiAttributeTile(name: "contact", type: "generic", width: 6, height: 4) {
			tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
				attributeState "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13"
				attributeState "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00A0DC"
			}
		}


		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label: '${currentValue}% battery', unit: ""
		}
        
		valueTile("voltage", "device.voltage", width: 2, height: 2) {
			state "voltage", label:'${currentValue} V', backgroundColor: "#cccccc"
		}

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}

	}
}

def parse(String description) {
	log.debug "description: $description"

	Map map = zigbee.getEvent(description)
	if (!map) {
		if (description?.startsWith('zone status')) {
        	if(description?.startsWith('zone status 0x6431')) {
				map = parseIasMessage1(description)
                log.debug "Contact 1 open: $map"
            } else if (description?.startsWith('zone status 0x6432')) {
				map = parseIasMessage2(description)
                log.debug "Contact 2 open: $map"
            } else {
				map = parseIasMessage1(description)
                log.debug "Contact closing: $map"
            } 
		} else if (description?.startsWith('catchall:')) {
					map = parseCatchAllMessage(description)
			} else {
			Map descMap = zigbee.parseDescriptionAsMap(description)
			if (descMap?.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
				map = getBatteryResult(Integer.parseInt(descMap.value, 16))
			} 
		}
	} 

	//log.debug "Parse returned $map"
	def result = map ? createEvent(map) : [:]

	if (description?.startsWith('enroll request')) {
		List cmds = zigbee.enrollResponse()
		log.debug "enroll response: ${cmds}"
		result = cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
	return result
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
	cluster.command == 0x0B ||
	(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	log.debug cluster
	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0001:
				// 0x07 - configure reporting
				if (cluster.command != 0x07) {
					resultMap = getBatteryResult(cluster.data.last())
				}
			break

		}
	}

	return resultMap
}


private Map parseIasMessage1(String description) {
	ZoneStatus zs = zigbee.parseZoneStatus(description)
    return zs.isAlarm1Set() ? getContactResult('open') : getContactResult('closed')     
}

private Map parseIasMessage2(String description) {
	ZoneStatus zs = zigbee.parseZoneStatus(description)
    return zs.isAlarm2Set() ? getContactResult('open') : getContactResult('closed')     
}

private Map getBatteryResult(rawValue) {
	log.debug 'Battery'
	def linkText = getLinkText(device)

    def result = [:]

	def volts = rawValue / 10
	if (!(rawValue == 0 || rawValue == 255)) {
		def minVolts = 2.1
		def maxVolts = 3.0
		def pct = (volts - minVolts) / (maxVolts - minVolts)
		def roundedPct = Math.round(pct * 100)
		if (roundedPct <= 0)
			roundedPct = 1
		result.value = Math.min(100, roundedPct)
		result.descriptionText = "${linkText} battery was ${result.value}%"
		result.name = 'battery'
	}
    
    log.debug "voltage: ${volts}v"
  //  sendEvent(name: "voltage", value: ${volts})

	return result
}

private Map getContactResult(value) {
	//log.debug 'Contact Status'
	def linkText = getLinkText(device)
    //log.debug "linkText: $linkText"
	def descriptionText = "${linkText} was ${value == 'open' ? 'opened' : 'closed'}"
	return [
			name           : 'contact',
			value          : value,
			descriptionText: descriptionText
	]
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return zigbee.readAttribute(0x001, 0x0020) // Read the Battery Level
}

def refresh() {

    log.debug "Refreshing Values"

    def refreshCmds = []
    refreshCmds +=zigbee.readAttribute(0x0001, 0x0020) // Read battery?
  //  refreshCmds += zigbee.readAttribute(0x0402, 0x0000) // Read temp?
   // refreshCmds += zigbee.readAttribute(0x0400, 0x0000) // Read luminance?
    refreshCmds += zigbee.readAttribute(0x0500, 0x0000) // Read status?

	return refreshCmds + zigbee.enrollResponse()
}

def configure() {
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

	log.debug "Configuring Reporting, IAS CIE, and Bindings."

	// temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
	return refresh() + zigbee.batteryConfig()  // send refresh cmds as part of config
}