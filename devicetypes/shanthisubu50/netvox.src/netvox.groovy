/**
 *  Netvox
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
	definition (name: "Netvox", namespace: "shanthisubu50", author: "SM TM") {
		capability "Battery"
		capability "Motion Sensor"
		capability "Sensor"
		capability "Temperature Measurement"

		fingerprint endpointId: "01", profileId: "0104", deviceId: "0402", deviceVersion: "00", inClusters: "0000 0001 0003 0015 0020 0500 0B05 "
		fingerprint endpointId: "02", profileId: "0104", deviceId: "0000", deviceVersion: "00", inClusters: "0000 0003 0B05 ", outClusters: "0006"
		fingerprint endpointId: "03", profileId: "0104", deviceId: "0302", deviceVersion: "00", inClusters: " 0000 0003 0402 0B05 "
	}


	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
		// TODO: define your main and details tiles here
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'battery' attribute
	// TODO: handle 'motion' attribute
	// TODO: handle 'temperature' attribute

}