/**
 *  Leviton
 *
 *  Copyright 2017 SM TM
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
	definition (name: "Leviton Occ Sensor", namespace: "shanthisubu50", author: "SM TM") {
		capability "Motion Sensor"
		capability "Sensor"
        capability "Battery"
		capability "Refresh"

		fingerprint endpointId: "01", profileId: "0104", deviceId: "0107", deviceVersion: "00", inClusters: " 0000,0001,0003,0020,0406 "
	}


	simulator {
		status "open": "open/closed: open"
		status "closed": "open/closed: closed"
	}

	preferences {
		section {
			input title: "Sensitivity",
                description: "This feature allows you to set the sensitivity'.",
                displayDuringSetup: true,
                type: "paragraph",
                element: "paragraph"
			input "Sensitivity",
               	"number",
                title: "%",
                description: "Adjust sensitivity by %",
                range: "*..*",
                displayDuringSetup: true
		}
	}

	tiles(scale: 2) {
		standardTile("contact", "device.contact",  width: 6, height: 4, key:"PRIMARY_CONTROL") {
			state "open", label: "open", icon: "st.contact.contact.open", backgroundColor: "#FF0000"
			state "closed", label: "closed", icon: "st.contact.contact.closed", backgroundColor: "#00CC00"
		}

		standardTile("battery", "device.battery", inactiveLabel: true, decoration: "flat", width: 2, height: 2) {
			state("battery", label: '${currentValue}', icon:"st.Appliances.appliances17")
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state("default", label:'refresh', action:"polling.poll", icon:"st.secondary.refresh-icon")
		}
	}
}

def parse(String description) {
	log.debug "description: $description"

	Map map = [:]
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
	else if (description?.startsWith('zone status')) {
		map = parseIasMessage(description)
	}

	// Temporary fix for the case when Device is OFFLINE and is connected again
	if (state.lastActivity == null){
		state.lastActivity = now()
		sendEvent(name: "deviceWatch-lastActivity", value: state.lastActivity, description: "Last Activity is on ${new Date((long)state.lastActivity)}", displayed: false, isStateChange: true)
	}
	state.lastActivity = now()

	log.debug "Parse returned $map"
	def result = map ? createEvent(map) : null

	if (description?.startsWith('enroll request')) {
		List cmds = enrollResponse()
		log.debug "enroll response: ${cmds}"
		result = cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
    log.debug "======================="
	return result
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0001:
				resultMap = getBatteryResult(cluster.data.last())
				break

			case 0x0406:
				log.debug 'motion'
				resultMap.name = 'motion'
				break
		}
	}

	return resultMap
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	// 0x07 is bind message
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
		cluster.command == 0x0B ||
		cluster.command == 0x07 ||
		(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return True// !ignoredMessage
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	log.debug "Desc Map: $descMap"

	Map resultMap = [:]

	if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
	}
	else if (descMap.cluster == "0406" && descMap.attrId == "0000") {
		def value = descMap.value.endsWith("01") ? "active" : "inactive"
		resultMap = getMotionResult(value)
	}

	return resultMap
}

/*
private Map parseIasMessage(String description) {
	ZoneStatus zs = zigbee.parseZoneStatus(description)
	log.debug zs
	// Some sensor models that use this DTH use alarm1 and some use alarm2 to signify motion
	return (zs.isAlarm1Set() || zs.isAlarm2Set()) ? getMotionResult('open') : getMotionResult('closed')
}
*/
private Map getBatteryResult(rawValue) {
	log.debug "Battery rawValue = ${rawValue}"
	def linkText = getLinkText(device)

	def result = [
		name: 'battery',
		value: '--',
		translatable: true
	]

	def volts = rawValue / 10

	if (rawValue == 0 || rawValue == 255) {}
	else {
		if (volts > 3.5) {
			result.descriptionText = "{{ device.displayName }} battery has too much power: (> 3.5) volts."
		}
		else {
      def minVolts = 2.1
      def maxVolts = 3.0
      def pct = (volts - minVolts) / (maxVolts - minVolts)
      def roundedPct = Math.round(pct * 100)
      if (roundedPct <= 0)
      roundedPct = 1
      result.value = Math.min(100, roundedPct)
      result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
		}
	}

	return result
}


private Map getMotionResult(value) {
	log.debug 'contact'
	String descriptionText = value == 'open' ? "{{ device.displayName }} detected open" : "{{ device.displayName }} detected closed"
	return [
		name: 'contact',
		value: value,
		descriptionText: descriptionText,
    translatable: true
	]
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {

	if (state.lastActivity < (now() - (1000 * device.currentValue("checkInterval"))) ){
		log.info "ping, alive=no, lastActivity=${state.lastActivity}"
		state.lastActivity = null
		return zigbee.readAttribute(0x001, 0x0020) // Read the Battery Level
	} else {
		log.info "ping, alive=yes, lastActivity=${state.lastActivity}"
		sendEvent(name: "deviceWatch-lastActivity", value: state.lastActivity, description: "Last Activity is on ${new Date((long)state.lastActivity)}", displayed: false, isStateChange: true)
	}
}

def refresh() {
	log.debug "refresh called"
//	def refreshCmds = [
//		"st rattr 0x${device.deviceNetworkId} 1 0x402 0", "delay 200",
//		"st rattr 0x${device.deviceNetworkId} 1 1 0x20", "delay 200"
//	]

//	return refreshCmds + enrollResponse()


	return [
    	zigbee.writeAttribute(0x0500, 0x0010, 0xf0, swapEndianHex(device.hub.zigbeeEui)),
        //zigbee.configureReporting(0x0402, 0x0000, 0x29, 30, 600, 1), //Temp signed 16bit int
        zigbee.configureReporting(0x0001, 0x0020, 0x20, 10, 3600, 1), //Power unsigned 8bit
        zigbee.configureReporting(0x0500, 0x0002, 0x19, 0, 30, null) //IAS 16bit bitmap
	]
}

def configure() {
	sendEvent(name: "checkInterval", value: 14400, displayed: false, data: [protocol: "zigbee"])
	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	log.debug "Configuring Reporting, IAS CIE, and Bindings."
	def configCmds = [
		"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500",
		"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 1 {${device.zigbeeId}} {}", "delay 200",
		"zcl global send-me-a-report 1 0x20 0x20 30 21600 {01}",		//checkin time 6 hrs
		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500",
		"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 0x402 {${device.zigbeeId}} {}", "delay 200",
		"zcl global send-me-a-report 0x402 0 0x29 300 3600 {6400}",
		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500"
	]
	return configCmds + refresh() // send refresh cmds as part of config
}


def enrollResponse() {
	log.debug "not Sending enroll response"
	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	[
		//Resending the CIE in case the enroll request is sent before CIE is written
		"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500",
		//Enroll Response
		"raw 0x500 {01 23 00 00 00}",
		"send 0x${device.deviceNetworkId} 1 1", "delay 200"
	]
}

private getEndpointId() {
	new BigInteger(device.endpointId, 16).toString()
}

private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
	int i = 0;
	int j = array.length - 1;
	byte tmp;
	while (j > i) {
		tmp = array[j];
		array[j] = array[i];
		array[i] = tmp;
		j--;
		i++;
	}
	return array
}